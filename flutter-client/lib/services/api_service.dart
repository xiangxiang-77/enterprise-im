import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:http/http.dart' as http;

class ApiService {
  ApiService({required this.baseUrl});

  String baseUrl;
  String? token;
  String? currentUserId;

  void updateBaseUrl(String host, String port) {
    baseUrl = 'http://$host:$port';
  }

  // Auth endpoints
  Future<Map<String, Object?>> login(String phone, String password) async {
    return post('/api/auth/login', {
      'phone': phone,
      'password': password,
    });
  }

  Future<Map<String, Object?>> sendSmsCode(String phone) async {
    return post('/api/auth/sms/send', {'phone': phone});
  }

  // Conversation endpoints
  Future<Map<String, Object?>> getConversations() async {
    return get('/api/conversations');
  }

  Future<Map<String, Object?>> getMessages(String conversationId) async {
    return get('/api/conversations/$conversationId/messages');
  }

  Future<Map<String, Object?>> sendMessage(String conversationId, String content) async {
    return post('/api/conversations/$conversationId/messages', {
      'content': content,
    });
  }

  Future<Map<String, Object?>> uploadFile(File file, {String? conversationId}) async {
    return uploadFileWithProgress(file, conversationId: conversationId);
  }

  Future<Map<String, Object?>> uploadFileWithProgress(
    File file, {
    String? conversationId,
    void Function(int sentBytes, int totalBytes)? onProgress,
  }) async {
    final totalBytes = await file.length();
    if (totalBytes >= 1024 * 1024) {
      return uploadFileInChunks(file, onProgress: onProgress);
    }
    return uploadFileMultipart(file, conversationId: conversationId, onProgress: onProgress);
  }

  Future<Map<String, Object?>> uploadFileInChunks(
    File file, {
    void Function(int sentBytes, int totalBytes)? onProgress,
    int chunkSize = 512 * 1024,
  }) async {
    final totalBytes = await file.length();
    final totalChunks = max(1, (totalBytes / chunkSize).ceil());
    final fileName = file.uri.pathSegments.last;
    final session = await post('/api/files/chunk-upload/sessions', {
      'uploaderId': _requiredUserId(),
      'originalName': fileName,
      'contentType': 'application/octet-stream',
      'totalSize': totalBytes,
      'totalChunks': totalChunks,
    });
    final sessionId = session['id']?.toString();
    if (sessionId == null || sessionId.isEmpty) {
      throw 'chunk session missing id';
    }

    var uploadedBytes = 0;
    for (var index = 0; index < totalChunks; index++) {
      final start = index * chunkSize;
      final end = min(start + chunkSize, totalBytes);
      final bytes = await file.openRead(start, end).fold<List<int>>(<int>[], (buffer, chunk) {
        buffer.addAll(chunk);
        return buffer;
      });
      await _uploadChunkWithRetry(sessionId, index, bytes, fileName);
      uploadedBytes += bytes.length;
      onProgress?.call(uploadedBytes, totalBytes);
    }
    return post('/api/files/chunk-upload/sessions/$sessionId/complete', {});
  }

  Future<void> _uploadChunkWithRetry(String sessionId, int index, List<int> bytes, String fileName) async {
    Object? lastError;
    for (var attempt = 1; attempt <= 3; attempt++) {
      try {
        final uri = Uri.parse('$baseUrl/api/files/chunk-upload/sessions/$sessionId/chunks?chunkIndex=$index');
        final request = http.MultipartRequest('POST', uri);
        if (token != null && token!.isNotEmpty) {
          request.headers[HttpHeaders.authorizationHeader] = 'Bearer $token';
        }
        request.files.add(http.MultipartFile.fromBytes('file', bytes, filename: '$fileName.part$index'));
        final response = await request.send().timeout(const Duration(seconds: 20));
        final responseBody = await response.stream.bytesToString();
        final decodedRaw = jsonDecode(responseBody);
        if (decodedRaw is! Map) throw 'unexpected response: $responseBody';
        final decoded = Map<String, Object?>.from(decodedRaw);
        if (response.statusCode >= 200 && response.statusCode < 300 && decoded['success'] == true) {
          return;
        }
        throw decoded['error'] ?? 'HTTP ${response.statusCode}';
      } catch (error) {
        lastError = error;
        if (attempt < 3) {
          await Future<void>.delayed(Duration(milliseconds: 300 * attempt));
        }
      }
    }
    throw lastError ?? 'chunk upload failed';
  }

  Future<Map<String, Object?>> uploadFileMultipart(
    File file, {
    String? conversationId,
    void Function(int sentBytes, int totalBytes)? onProgress,
  }) async {
    final totalBytes = await file.length();
    final uri = Uri.parse('$baseUrl/api/files/upload');
    final boundary = '----enterprise-im-${DateTime.now().microsecondsSinceEpoch}-${Random().nextInt(1 << 32)}';
    final client = HttpClient();
    try {
      final request = await client.postUrl(uri).timeout(const Duration(seconds: 5));
      request.headers.contentType = ContentType('multipart', 'form-data', parameters: {'boundary': boundary});
      if (token != null && token!.isNotEmpty) {
        request.headers.set(HttpHeaders.authorizationHeader, 'Bearer $token');
      }

      void writeField(String name, String value) {
        request.write('--$boundary\r\n');
        request.write('Content-Disposition: form-data; name="$name"\r\n\r\n');
        request.write('$value\r\n');
      }

      if (conversationId != null && conversationId.isNotEmpty) {
        writeField('conversationId', conversationId);
      }
      writeField('uploaderId', _requiredUserId());
      request.write('--$boundary\r\n');
      request.write('Content-Disposition: form-data; name="file"; filename="${file.uri.pathSegments.last}"\r\n');
      request.write('Content-Type: application/octet-stream\r\n\r\n');

      var sentBytes = 0;
      await for (final chunk in file.openRead()) {
        request.add(chunk);
        sentBytes += chunk.length;
        onProgress?.call(sentBytes, totalBytes);
      }
      request.write('\r\n--$boundary--\r\n');

      final response = await request.close().timeout(const Duration(seconds: 30));
      final responseBody = await response.transform(utf8.decoder).join();
      final decodedRaw = jsonDecode(responseBody);
      if (decodedRaw is! Map) {
        throw 'unexpected response: $responseBody';
      }
      final decoded = Map<String, Object?>.from(decodedRaw);
      if (response.statusCode < 200 || response.statusCode >= 300 || decoded['success'] != true) {
        throw decoded['error'] ?? 'HTTP ${response.statusCode}';
      }
      final data = decoded['data'];
      if (data is Map<String, Object?>) {
        return data;
      }
      return {'items': data};
    } finally {
      client.close(force: true);
    }
  }

  Future<Map<String, Object?>> uploadFileLegacy(File file, {String? conversationId}) async {
    final uri = Uri.parse('$baseUrl/api/files/upload');
    final request = http.MultipartRequest('POST', uri);
    if (token != null && token!.isNotEmpty) {
      request.headers[HttpHeaders.authorizationHeader] = 'Bearer $token';
    }
    if (conversationId != null && conversationId.isNotEmpty) {
      request.fields['conversationId'] = conversationId;
    }
    request.fields['uploaderId'] = _requiredUserId();
    request.files.add(await http.MultipartFile.fromPath('file', file.path));
    final response = await request.send().timeout(const Duration(seconds: 30));
    final responseBody = await response.stream.bytesToString();
    final decodedRaw = jsonDecode(responseBody);
    if (decodedRaw is! Map) {
      throw 'unexpected response: $responseBody';
    }
    final decoded = Map<String, Object?>.from(decodedRaw);
    if (response.statusCode < 200 || response.statusCode >= 300 || decoded['success'] != true) {
      throw decoded['error'] ?? 'HTTP ${response.statusCode}';
    }
    final data = decoded['data'];
    if (data is Map<String, Object?>) {
      return data;
    }
    return {'items': data};
  }

  Future<Map<String, Object?>> forwardMessages(
    List<String> messageIds,
    List<String> targetConversationIds, {
    String mode = 'single',
  }) async {
    return post('/api/messages/forward', {
      'messageIds': messageIds,
      'targetConversationIds': targetConversationIds,
      'mode': mode,
    });
  }

  Future<Map<String, Object?>> updateConversationSettings(
    String conversationId,
    Map<String, Object?> settings,
  ) async {
    return patch('/api/conversations/$conversationId/settings', settings);
  }

  Future<Map<String, Object?>> updateGroupNotice(String groupId, String notice) async {
    return patch('/api/groups/$groupId/notice', {'notice': notice});
  }

  Future<Map<String, Object?>> transferGroupOwnership(String groupId, String newOwnerId) async {
    return patch('/api/groups/$groupId/owner', {'ownerId': newOwnerId});
  }

  Future<Map<String, Object?>> leaveGroup(String groupId) async {
    return del('/api/groups/$groupId');
  }

  Future<Map<String, Object?>> clearConversationMessages(String conversationId) async {
    return del('/api/conversations/$conversationId/messages');
  }

  // Friends / contacts endpoints
  Future<Map<String, Object?>> getFriends() async {
    final userId = _requiredUserId();
    return get('/api/friends?userId=${Uri.encodeQueryComponent(userId)}');
  }

  Future<Map<String, Object?>> getDirectoryUsers() async {
    return get('/api/directory/users');
  }

  // Message operation endpoints
  Future<Map<String, Object?>> favoriteMessage(String messageId) async {
    return post('/api/messages/$messageId/favorite', {});
  }

  Future<Map<String, Object?>> likeMessage(String messageId) async {
    return post('/api/messages/$messageId/reactions', {'reaction': 'like'});
  }

  Future<Map<String, Object?>> recallMessage(String messageId, {String reason = 'mobile_recall'}) async {
    return post('/api/messages/$messageId/recall', {'reason': reason});
  }

  Future<Map<String, Object?>> editMessage(String messageId, String content) async {
    return patch('/api/messages/$messageId/edit', {'content': content});
  }

  Future<Map<String, Object?>> getMessageReadStatus(String messageId) async {
    return get('/api/messages/$messageId/read-status');
  }

  // Call endpoints
  Future<Map<String, Object?>> getCallReadiness() async {
    return get('/api/calls/readiness');
  }

  Future<Map<String, Object?>> startCall({
    required String callerId,
    required String calleeId,
    required String conversationId,
    required String mediaType,
  }) async {
    return post('/api/calls', {
      'callerId': callerId,
      'calleeId': calleeId,
      'conversationId': conversationId,
      'mediaType': mediaType,
    });
  }

  Future<Map<String, Object?>> getCalls(String userId, {int limit = 20}) async {
    return get('/api/calls?userId=${Uri.encodeQueryComponent(userId)}&limit=$limit');
  }

  Future<Map<String, Object?>> transitionCall(String callId, String action, String actorId) async {
    return post('/api/calls/$callId/$action', {'actorId': actorId});
  }

  Future<Map<String, Object?>> getMediaConfig({
    required String userId,
    required String calleeId,
    required String platform,
  }) async {
    return get('/api/calls/media-config?userId=${Uri.encodeQueryComponent(userId)}&calleeId=${Uri.encodeQueryComponent(calleeId)}&platform=$platform');
  }

  // Friend request endpoints
  Future<Map<String, Object?>> getFriendRequests() async {
    final userId = _requiredUserId();
    return get('/api/friend-requests?userId=${Uri.encodeQueryComponent(userId)}&box=incoming');
  }

  Future<Map<String, Object?>> handleFriendRequest(String requestId, bool accept) async {
    return post('/api/friend-requests/$requestId/handle', {'accept': accept});
  }

  // Group members
  Future<Map<String, Object?>> getGroupMembers(String groupId) async {
    return get('/api/groups/$groupId/members');
  }

  // Online status
  Future<Map<String, Object?>> getOnlineStatus(List<String> userIds) async {
    final ids = userIds.map((id) => Uri.encodeQueryComponent(id)).join(',');
    return get('/api/users/online-status?userIds=$ids');
  }

  // Search endpoint
  Future<Map<String, Object?>> search(String query, {String type = 'all'}) async {
    return get('/api/search?q=${Uri.encodeQueryComponent(query)}&type=$type');
  }

  // User detail endpoint
  Future<Map<String, Object?>> getUserDetail(String userId) async {
    return get('/api/users/$userId');
  }

  // Notification settings endpoint
  Future<Map<String, Object?>> getNotificationSettings() async {
    return get('/api/notification-settings');
  }

  Future<Map<String, Object?>> updateNotificationSettings(Map<String, Object?> updates) async {
    return patch('/api/notification-settings', updates);
  }

  // Friend management endpoints
  Future<Map<String, Object?>> updateFriendRemark(String userId, String remark) async {
    return patch('/api/friends/$userId/remark', {'remark': remark});
  }

  Future<Map<String, Object?>> deleteFriend(String userId) async {
    return del('/api/friends/$userId');
  }

  Future<Map<String, Object?>> favoriteContact(String userId) async {
    return post('/api/friends/$userId/favorite', {});
  }

  Future<Map<String, Object?>> blacklistUser(String userId) async {
    return post('/api/users/$userId/blacklist', {});
  }

  Future<Map<String, Object?>> unblacklistUser(String userId) async {
    return del('/api/users/$userId/blacklist');
  }

  Future<Map<String, Object?>> getBlacklist() async {
    return get('/api/users/blacklist');
  }

  // Debug endpoints
  Future<Map<String, Object?>> uploadNativeLogs({
    required String userId,
    required String event,
    required String text,
  }) async {
    return post('/api/debug/mobile/logs', {
      'userId': userId,
      'event': event,
      'text': text,
    });
  }

  // HTTP method helpers
  Future<Map<String, Object?>> get(String path) async {
    return _request('GET', path);
  }

  Future<Map<String, Object?>> post(String path, Map<String, Object?> body) async {
    return _request('POST', path, body: body);
  }

  Future<Map<String, Object?>> patch(String path, Map<String, Object?> body) async {
    return _request('PATCH', path, body: body);
  }

  Future<Map<String, Object?>> del(String path) async {
    return _request('DELETE', path);
  }

  Future<Map<String, Object?>> _request(String method, String path, {Map<String, Object?>? body}) async {
    final client = HttpClient();
    try {
      final uri = Uri.parse('$baseUrl$path');
      final request = await client.openUrl(method, uri).timeout(const Duration(seconds: 5));
      request.headers.contentType = ContentType.json;
      if (token != null && token!.isNotEmpty) {
        request.headers.set(HttpHeaders.authorizationHeader, 'Bearer $token');
      }
      if (body != null) {
        request.write(jsonEncode(body));
      }
      final response = await request.close().timeout(const Duration(seconds: 10));
      final responseBody = await response.transform(utf8.decoder).join();
      final decodedRaw = jsonDecode(responseBody);
      if (decodedRaw is! Map) {
        throw 'unexpected response: $responseBody';
      }
      final decoded = Map<String, Object?>.from(decodedRaw);
      if (response.statusCode < 200 || response.statusCode >= 300 || decoded['success'] != true) {
        throw decoded['error'] ?? 'HTTP ${response.statusCode}';
      }
      final data = decoded['data'];
      if (data is Map<String, Object?>) {
        return data;
      }
      return {'items': data};
    } finally {
      client.close(force: true);
    }
  }

  String _requiredUserId() {
    final userId = currentUserId;
    if (userId == null || userId.isEmpty) {
      throw 'missing current user id';
    }
    return userId;
  }
}
