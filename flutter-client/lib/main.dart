import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as path;
import 'package:sqflite/sqflite.dart';

void main() {
  runApp(const EnterpriseImApp());
}

class EnterpriseImApp extends StatelessWidget {
  const EnterpriseImApp({super.key});

  @override
  Widget build(BuildContext context) {
    const seed = Color(0xFF2563EB);
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: '企业 IM',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: seed),
        scaffoldBackgroundColor: const Color(0xFFF6F8FB),
        useMaterial3: true,
      ),
      home: const MobileClientPage(),
    );
  }
}

class ChatEntry {
  ChatEntry({
    required this.sender,
    required this.content,
    required this.direction,
    required this.createdAt,
  });

  final String sender;
  final String content;
  final String direction;
  final DateTime createdAt;

  bool get mine => direction == 'out';
}

class MobileClientPage extends StatefulWidget {
  const MobileClientPage({super.key});

  @override
  State<MobileClientPage> createState() => _MobileClientPageState();
}

class _MobileClientPageState extends State<MobileClientPage> {
  static const sipChannel = MethodChannel('enterprise_im/sip');

  final hostController = TextEditingController(text: '10.200.71.31');
  final httpPortController = TextEditingController(text: '18080');
  final tcpPortController = TextEditingController(text: '19090');
  final userIdController = TextEditingController(text: 'u_flutter');
  final tokenController = TextEditingController(text: 'demo-token-u_flutter');
  final peerIdController = TextEditingController(text: 'u_qt');
  final conversationIdController = TextEditingController(text: 'c_qt_flutter');
  final messageController = TextEditingController();

  final logs = <String>[];
  final messages = <ChatEntry>[
    ChatEntry(
      sender: '系统',
      content: '企业 IM 手机端 v7 已就绪：SIP registrar 修复版 2026-05-20',
      direction: 'system',
      createdAt: DateTime.now(),
    ),
  ];

  Database? localDb;
  Socket? socket;
  StreamSubscription<List<int>>? subscription;
  String pending = '';
  bool connected = false;
  bool authenticated = false;
  bool callLoading = false;
  Map<String, Object?>? activeCall;
  Map<String, Object?>? readiness;
  Map<String, Object?>? mediaConfig;
  List<Map<String, Object?>> calls = [];
  String sipStatus = 'idle';
  String? nativeCallId;
  CameraController? cameraController;
  Future<void>? cameraInitFuture;
  String cameraStatus = 'idle';

  String get baseUrl => 'http://${hostController.text.trim()}:${httpPortController.text.trim()}';
  String get activeStatus => activeCall?['status']?.toString() ?? 'none';
  String get selfId => userIdController.text.trim();
  String get activeCallerId => activeCall?['callerId']?.toString() ?? '';
  String get activeCalleeId => activeCall?['calleeId']?.toString() ?? '';
  bool get activeCallIsMine => activeCallerId == selfId;
  bool get activeCallIsForMe => activeCalleeId == selfId;
  bool get incomingRinging => activeStatus == 'ringing' && activeCallIsForMe && !activeCallIsMine;
  bool get outgoingRinging => activeStatus == 'ringing' && activeCallIsMine;
  bool get activeAnswered => activeStatus == 'answered';
  bool get activeCallVisible => activeStatus == 'ringing' || activeStatus == 'answered';
  bool get activeVideoCall => activeCall?['mediaType']?.toString() == 'video';
  bool get canHangupCall => activeStatus == 'ringing' || activeStatus == 'answered';

  @override
  void initState() {
    super.initState();
    sipChannel.setMethodCallHandler(handleSipChannelCall);
    unawaited(initLocalStore());
    unawaited(loadCallReadiness());
  }

  @override
  void dispose() {
    sipChannel.setMethodCallHandler(null);
    subscription?.cancel();
    socket?.destroy();
    cameraController?.dispose();
    localDb?.close();
    hostController.dispose();
    httpPortController.dispose();
    tcpPortController.dispose();
    userIdController.dispose();
    tokenController.dispose();
    peerIdController.dispose();
    conversationIdController.dispose();
    messageController.dispose();
    super.dispose();
  }

  Future<void> handleSipChannelCall(MethodCall call) async {
    if (call.method != 'sipEvent') return;
    final args = call.arguments;
    final event = args is Map ? Map<Object?, Object?>.from(args) : <Object?, Object?>{};
    final type = event['type']?.toString() ?? 'event';
    final message = event['message']?.toString() ?? '';
    if (!mounted) return;
    setState(() {
      if (type == 'error') {
        sipStatus = message.isEmpty ? 'error' : 'error: ${shortSipMessage(message)}';
      } else if (type == 'media' || type == 'video' || type == 'state' || type == 'registration' || type == 'call') {
        sipStatus = message.isEmpty ? type : shortSipMessage(message);
      }
      addLog('SIP EVENT $type $message');
    });
  }

  String shortSipMessage(String message) {
    final compact = message.replaceAll(RegExp(r'\s+'), ' ').trim();
    if (compact.length <= 80) return compact;
    return '${compact.substring(0, 77)}...';
  }

  Future<void> initLocalStore() async {
    final dbPath = path.join(await getDatabasesPath(), 'enterprise_im_mobile.db');
    final db = await openDatabase(
      dbPath,
      version: 1,
      onCreate: (database, version) async {
        await database.execute(
          'CREATE TABLE local_logs(id INTEGER PRIMARY KEY AUTOINCREMENT, line TEXT NOT NULL, created_at TEXT NOT NULL)',
        );
        await database.execute(
          'CREATE TABLE local_messages(id INTEGER PRIMARY KEY AUTOINCREMENT, conversation_id TEXT, sender TEXT, content TEXT NOT NULL, direction TEXT NOT NULL, created_at TEXT NOT NULL)',
        );
        await database.execute(
          'CREATE TABLE local_calls(id INTEGER PRIMARY KEY AUTOINCREMENT, call_id TEXT NOT NULL, media_type TEXT, status TEXT, created_at TEXT NOT NULL)',
        );
      },
    );
    localDb = db;
    await loadLocalMessages();
    if (mounted) {
      setState(() => addLog('SQLITE READY $dbPath'));
    }
  }

  Future<void> loadLocalMessages() async {
    final db = localDb;
    if (db == null || !db.isOpen) return;
    final rows = await db.query(
      'local_messages',
      where: 'conversation_id = ?',
      whereArgs: [conversationIdController.text.trim()],
      orderBy: 'id DESC',
      limit: 30,
    );
    final loaded = rows.reversed.map((row) {
      return ChatEntry(
        sender: row['sender']?.toString() ?? '',
        content: row['content']?.toString() ?? '',
        direction: row['direction']?.toString() ?? 'in',
        createdAt: DateTime.tryParse(row['created_at']?.toString() ?? '') ?? DateTime.now(),
      );
    }).toList();
    if (mounted && loaded.isNotEmpty) {
      setState(() {
        messages
          ..clear()
          ..addAll(loaded);
      });
    }
  }

  Future<void> connectSocket() async {
    if (connected) {
      await subscription?.cancel();
      socket?.destroy();
      setState(() {
        connected = false;
        authenticated = false;
        addLog('TCP DISCONNECTED');
      });
      return;
    }

    try {
      final port = int.parse(tcpPortController.text.trim());
      final nextSocket = await Socket.connect(hostController.text.trim(), port, timeout: const Duration(seconds: 5));
      subscription = nextSocket.listen(onData, onError: (error) {
        setState(() => addLog('TCP ERROR $error'));
      }, onDone: () {
        setState(() {
          connected = false;
          authenticated = false;
          addLog('TCP DISCONNECTED');
        });
      });
      setState(() {
        socket = nextSocket;
        connected = true;
        authenticated = false;
        addLog('TCP CONNECTED ${hostController.text}:$port');
      });
      sendAuth();
    } catch (error) {
      setState(() => addLog('TCP ERROR $error'));
    }
  }

  void onData(List<int> bytes) {
    pending += utf8.decode(bytes);
    var newline = pending.indexOf('\n');
    while (newline >= 0) {
      final line = pending.substring(0, newline).trim();
      pending = pending.substring(newline + 1);
      if (line.isNotEmpty) {
        handleSocketLine(line);
      }
      newline = pending.indexOf('\n');
    }
  }

  void handleSocketLine(String line) {
    try {
      final decoded = jsonDecode(line);
      if (decoded is Map) {
        final frame = Map<String, Object?>.from(decoded);
        final type = frame['type']?.toString();
        final payload = frame['payload'];
        if (type == 'AUTH_OK') {
          setState(() {
            authenticated = true;
            addLog('TCP AUTH OK ${payload ?? ''}');
          });
          return;
        }
        if (type == 'AUTH_FAILED') {
          setState(() {
            authenticated = false;
            addLog('TCP AUTH FAILED ${payload ?? ''}');
          });
          return;
        }
        if ((type == 'CALL_INVITE' || type == 'CALL_UPDATE') && payload is Map) {
          final call = Map<String, Object?>.from(payload);
          unawaited(saveLocalCall(call));
          setState(() {
            activeCall = call;
            addLog('TCP $type active=${call['id']} status=${call['status']}');
          });
          syncCameraPreviewForCall(call);
          if (call['status']?.toString() == 'answered' && call['mediaStatus'] == 'media_ready') {
            unawaited(startNativeSipForCall(call));
          }
          if (call['status']?.toString() == 'rejected' || call['status']?.toString() == 'ended') {
            unawaited(stopNativeSip());
            unawaited(stopCameraPreview());
          }
          return;
        }
        if (type == 'TEXT_DELIVER' && payload is Map) {
          final content = payload['content']?.toString() ?? '';
          final sender = frame['from']?.toString() ?? peerIdController.text.trim();
          unawaited(saveLocalMessage(
            conversationId: frame['conversationId']?.toString(),
            sender: sender,
            content: content,
            direction: 'in',
          ));
          setState(() {
            messages.add(ChatEntry(sender: sender, content: content, direction: 'in', createdAt: DateTime.now()));
          });
        }
      }
    } catch (_) {
      // Keep raw logging for malformed frames.
    }
    setState(() => addLog('TCP IN $line'));
  }

  void sendAuth() {
    sendFrame('AUTH', payload: {'token': tokenController.text.trim()});
  }

  void sendPing() {
    sendFrame('PING');
  }

  void sendText() {
    final content = messageController.text.trim();
    if (content.isEmpty) return;
    sendFrame(
      'TEXT',
      to: peerIdController.text.trim(),
      conversationId: conversationIdController.text.trim(),
      payload: {'content': content},
    );
    messageController.clear();
    unawaited(saveLocalMessage(
      conversationId: conversationIdController.text.trim(),
      sender: userIdController.text.trim(),
      content: content,
      direction: 'out',
    ));
    setState(() {
      messages.add(ChatEntry(
        sender: userIdController.text.trim(),
        content: content,
        direction: 'out',
        createdAt: DateTime.now(),
      ));
    });
  }

  void sendFrame(String type, {String? to, String? conversationId, Map<String, Object?> payload = const {}}) {
    final currentSocket = socket;
    if (currentSocket == null || !connected) {
      setState(() => addLog('TCP ERROR socket is not connected'));
      return;
    }

    final frame = <String, Object?>{
      'version': '1',
      'type': type,
      'requestId': '${DateTime.now().microsecondsSinceEpoch}',
      'from': userIdController.text.trim(),
      'timestamp': DateTime.now().millisecondsSinceEpoch,
      'payload': payload,
    };
    if (to != null && to.isNotEmpty) {
      frame['to'] = to;
    }
    if (conversationId != null && conversationId.isNotEmpty) {
      frame['conversationId'] = conversationId;
    }

    final line = jsonEncode(frame);
    currentSocket.write('$line\n');
    setState(() => addLog('TCP OUT $line'));
  }

  Future<void> loadCallReadiness() async {
    await runCallAction(() async {
      final data = await getJson('/api/calls/readiness');
      readiness = data;
      addLog('CALL READINESS ${jsonEncode(data)}');
    }, quiet: true);
  }

  Future<void> startCall(String mediaType) async {
    await runCallAction(() async {
      activeCall = null;
      final data = await postJson('/api/calls', {
        'callerId': userIdController.text.trim(),
        'calleeId': peerIdController.text.trim(),
        'conversationId': conversationIdController.text.trim(),
        'mediaType': mediaType,
      });
      activeCall = data;
      await saveLocalCall(data);
      addLog('CALL START ${jsonEncode(data)}');
      syncCameraPreviewForCall(data);
      await refreshCalls();
    });
  }

  Future<void> answerCall() async {
    if (!incomingRinging) {
      setState(() => addLog('CALL ANSWER BLOCKED not incoming ringing'));
      return;
    }
    await transitionCall('answer');
  }

  Future<void> rejectCall() async {
    await transitionCall('reject');
  }

  Future<void> hangupCall() async {
    await transitionCall('hangup');
  }

  Future<void> transitionCall(String action) async {
    final callId = activeCall?['id']?.toString();
    if (callId == null || callId.isEmpty) {
      setState(() => addLog('CALL ERROR no active call'));
      return;
    }
    await runCallAction(() async {
      final data = await postJson('/api/calls/$callId/$action', {'actorId': userIdController.text.trim()});
      activeCall = data;
      await saveLocalCall(data);
      addLog('CALL ${action.toUpperCase()} ${jsonEncode(data)}');
      syncCameraPreviewForCall(data);
      if (action == 'answer' && data['mediaStatus'] == 'media_ready') {
        await startNativeSipForCall(data);
      }
      if (action == 'reject' || action == 'hangup') {
        await stopNativeSip();
        await stopCameraPreview();
      }
      await refreshCalls();
    });
  }

  Future<void> startNativeSipForCall(Map<String, Object?> call) async {
    final callId = call['id']?.toString() ?? '';
    if (callId.isNotEmpty && nativeCallId == callId) {
      addLog('SIP already started for $callId');
      return;
    }
    final peerId = peerIdForCall(call);
    final outbound = call['callerId']?.toString() == userIdController.text.trim();
    final config = await getJson(
      '/api/calls/media-config?userId=${Uri.encodeQueryComponent(userIdController.text.trim())}&calleeId=${Uri.encodeQueryComponent(peerId)}&platform=android',
    );
    mediaConfig = config;
    try {
      final result = await sipChannel.invokeMethod<Map<Object?, Object?>>('start', {
        'callId': callId,
        'mediaType': call['mediaType']?.toString() ?? 'audio',
        'outbound': outbound,
        'sipDomain': config['sipDomain']?.toString() ?? '',
        'sipRegistrar': config['sipRegistrar']?.toString() ?? '',
        'sipRealm': config['sipRealm']?.toString() ?? '',
        'sipUsername': config['sipUsername']?.toString() ?? '',
        'sipPassword': config['sipPassword']?.toString() ?? '',
        'selfSipUri': config['selfSipUri']?.toString() ?? '',
        'calleeSipUri': config['calleeSipUri']?.toString() ?? '',
        'turnUrl': config['turnUrl']?.toString() ?? '',
        'turnUsername': config['turnUsername']?.toString() ?? '',
        'turnPassword': config['turnPassword']?.toString() ?? '',
      });
      if (!mounted) return;
      setState(() {
        sipStatus = result?['status']?.toString() ?? 'unknown';
        nativeCallId = callId;
        addLog('SIP START $sipStatus ${jsonEncode(result)}');
      });
    } on PlatformException catch (error) {
      final message = [error.code, error.message]
          .where((part) => part != null && part.toString().isNotEmpty)
          .join(': ');
      if (!mounted) return;
      setState(() {
        sipStatus = 'error: ${shortSipMessage(message)}';
        addLog('SIP ERROR ${error.code}: ${error.message}');
      });
    }
  }

  Future<void> stopNativeSip() async {
    try {
      final result = await sipChannel.invokeMethod<Map<Object?, Object?>>('stop');
      if (!mounted) return;
      setState(() {
        sipStatus = result?['status']?.toString() ?? 'stopped';
        nativeCallId = null;
        addLog('SIP STOP $sipStatus');
      });
    } on PlatformException catch (error) {
      if (!mounted) return;
      setState(() {
        sipStatus = 'error: ${shortSipMessage('${error.code}: ${error.message ?? ''}')}';
        addLog('SIP STOP ERROR ${error.code}: ${error.message}');
      });
    }
  }

  void syncCameraPreviewForCall(Map<String, Object?> call) {
    final status = call['status']?.toString() ?? '';
    final isVideo = call['mediaType']?.toString() == 'video';
    if (isVideo && (status == 'ringing' || status == 'answered')) {
      unawaited(startCameraPreview());
    } else {
      unawaited(stopCameraPreview());
    }
  }

  Future<void> startCameraPreview() async {
    if (cameraController?.value.isInitialized == true) {
      return;
    }
    try {
      setState(() => cameraStatus = 'starting');
      final cameras = await availableCameras();
      if (cameras.isEmpty) {
        setState(() => cameraStatus = 'no_camera');
        addLog('CAMERA ERROR no camera');
        return;
      }
      final frontCamera = cameras.firstWhere(
        (camera) => camera.lensDirection == CameraLensDirection.front,
        orElse: () => cameras.first,
      );
      final controller = CameraController(
        frontCamera,
        ResolutionPreset.medium,
        enableAudio: false,
      );
      cameraController = controller;
      cameraInitFuture = controller.initialize();
      await cameraInitFuture;
      if (mounted) {
        setState(() => cameraStatus = 'ready');
      }
      addLog('CAMERA READY ${frontCamera.name}');
    } catch (error) {
      setState(() => cameraStatus = 'error');
      addLog('CAMERA ERROR $error');
    }
  }

  Future<void> stopCameraPreview() async {
    final controller = cameraController;
    cameraController = null;
    cameraInitFuture = null;
    if (mounted && cameraStatus != 'idle') {
      setState(() => cameraStatus = 'idle');
    }
    await controller?.dispose();
  }

  String peerIdForCall(Map<String, Object?> call) {
    final self = userIdController.text.trim();
    final caller = call['callerId']?.toString() ?? peerIdController.text.trim();
    final callee = call['calleeId']?.toString() ?? peerIdController.text.trim();
    return caller == self ? callee : caller;
  }

  Future<void> refreshCalls() async {
    final userId = Uri.encodeQueryComponent(userIdController.text.trim());
    final data = await getJson('/api/calls?userId=$userId&limit=20');
    calls = dataList(data);
    if (activeCall == null && calls.isNotEmpty) {
      activeCall = calls.first;
    }
  }

  Future<void> loadCalls() async {
    await runCallAction(() async {
      await refreshCalls();
      addLog('CALL LIST ${calls.length}');
    });
  }

  Future<void> runCallAction(Future<void> Function() action, {bool quiet = false}) async {
    setState(() => callLoading = true);
    try {
      await action();
    } catch (error) {
      if (!quiet) {
        setState(() => addLog('CALL ERROR $error'));
      }
    } finally {
      if (mounted) {
        setState(() => callLoading = false);
      }
    }
  }

  Future<Map<String, Object?>> getJson(String requestPath) async {
    return requestJson('GET', requestPath);
  }

  Future<Map<String, Object?>> postJson(String requestPath, Map<String, Object?> body) async {
    return requestJson('POST', requestPath, body: body);
  }

  Future<Map<String, Object?>> requestJson(String method, String requestPath, {Map<String, Object?>? body}) async {
    final client = HttpClient();
    try {
      final uri = Uri.parse('$baseUrl$requestPath');
      final request = await client.openUrl(method, uri).timeout(const Duration(seconds: 5));
      request.headers.contentType = ContentType.json;
      final token = tokenController.text.trim();
      if (token.isNotEmpty) {
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

  List<Map<String, Object?>> dataList(Map<String, Object?> data) {
    final items = data['items'];
    if (items is List) {
      return items.whereType<Map>().map((item) => Map<String, Object?>.from(item)).toList();
    }
    return [];
  }

  void selectCall(Map<String, Object?> call) {
    setState(() {
      activeCall = call;
      addLog('CALL SELECT ${call['id']}');
    });
  }

  void addLog(String line) {
    final entry = '${DateTime.now().toIso8601String().substring(11, 23)} $line';
    logs.insert(0, entry);
    unawaited(saveLocalLog(entry));
  }

  Future<void> saveLocalLog(String line) async {
    final db = localDb;
    if (db == null || !db.isOpen) return;
    await db.insert('local_logs', {
      'line': line,
      'created_at': DateTime.now().toIso8601String(),
    });
  }

  Future<void> saveLocalMessage({
    required String? conversationId,
    required String sender,
    required String content,
    required String direction,
  }) async {
    final db = localDb;
    if (db == null || !db.isOpen || content.isEmpty) return;
    await db.insert('local_messages', {
      'conversation_id': conversationId,
      'sender': sender,
      'content': content,
      'direction': direction,
      'created_at': DateTime.now().toIso8601String(),
    });
  }

  Future<void> saveLocalCall(Map<String, Object?> call) async {
    final db = localDb;
    final callId = call['id']?.toString();
    if (db == null || !db.isOpen || callId == null || callId.isEmpty) return;
    await db.insert('local_calls', {
      'call_id': callId,
      'media_type': call['mediaType']?.toString(),
      'status': call['status']?.toString(),
      'created_at': DateTime.now().toIso8601String(),
    });
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final statusText = authenticated ? '在线' : (connected ? '连接中' : '离线');
    final statusColor = authenticated ? const Color(0xFF16A34A) : const Color(0xFF64748B);

    if (activeCallVisible && activeCall != null) {
      return _CallPage(
        call: activeCall!,
        peerId: peerIdForCall(activeCall!),
        sipStatus: sipStatus,
        cameraController: cameraController,
        cameraInitFuture: cameraInitFuture,
        cameraStatus: cameraStatus,
        onAnswer: incomingRinging && !callLoading ? answerCall : null,
        onReject: incomingRinging && !callLoading ? rejectCall : null,
        onHangup: canHangupCall && !callLoading ? hangupCall : null,
        onDebug: showDebugSheet,
      );
    }

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 16,
        title: Row(
          children: [
            CircleAvatar(
              radius: 17,
              backgroundColor: statusColor.withAlpha(31),
              child: Icon(Icons.business, size: 19, color: statusColor),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('企业 IM · v7 SIP注册修复', style: TextStyle(fontWeight: FontWeight.w700)),
                  Text('$statusText · ${peerIdController.text.trim()}', style: Theme.of(context).textTheme.bodySmall),
                ],
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: authenticated ? '断开' : '连接',
            onPressed: connectSocket,
            icon: Icon(authenticated ? Icons.cloud_done : (connected ? Icons.cloud_sync : Icons.cloud_off)),
          ),
          IconButton(
            tooltip: '设置',
            onPressed: showSettingsSheet,
            icon: const Icon(Icons.tune),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            _PeerHeader(
              peerId: peerIdController.text.trim(),
              statusText: statusText,
              statusColor: statusColor,
              callText: callStatusText(),
              onAudio: callLoading ? null : () => startCall('audio'),
              onVideo: callLoading ? null : () => startCall('video'),
              onHangup: canHangupCall && !callLoading ? hangupCall : null,
              onDebug: showDebugSheet,
            ),
            if (outgoingRinging) _OutgoingCallBar(mediaType: activeCall?['mediaType']?.toString() ?? 'audio', onHangup: hangupCall),
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.fromLTRB(14, 10, 14, 14),
                itemCount: messages.length,
                itemBuilder: (context, index) => _MessageBubble(entry: messages[index]),
              ),
            ),
            if (incomingRinging) _IncomingCallBar(
              mediaType: activeCall?['mediaType']?.toString() ?? 'audio',
              callerId: activeCallerId,
              onAnswer: answerCall,
              onReject: rejectCall,
            ),
            DecoratedBox(
              decoration: BoxDecoration(
                color: colorScheme.surface,
                border: Border(top: BorderSide(color: colorScheme.outlineVariant)),
              ),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 9, 12, 12),
                child: Row(
                  children: [
                    IconButton.filledTonal(
                      onPressed: sendPing,
                      tooltip: '心跳',
                      icon: const Icon(Icons.sync),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: TextField(
                        controller: messageController,
                        minLines: 1,
                        maxLines: 4,
                        textInputAction: TextInputAction.send,
                        onSubmitted: (_) => sendText(),
                        decoration: InputDecoration(
                          hintText: authenticated ? '输入消息' : '先连接认证',
                          filled: true,
                          fillColor: const Color(0xFFF8FAFC),
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none),
                          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    IconButton.filled(
                      onPressed: authenticated ? sendText : null,
                      tooltip: '发送',
                      icon: const Icon(Icons.send),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String callStatusText() {
    final ready = readiness?['ready'] == true;
    final mediaText = ready ? '音视频就绪' : '音视频检测中';
    if (activeStatus == 'none') return mediaText;
    if (incomingRinging) return '$mediaText · 来电';
    if (outgoingRinging) return '$mediaText · 等待接听';
    if (activeStatus == 'ringing') return '$mediaText · 呼叫中';
    if (activeStatus == 'answered') return '$mediaText · 通话中';
    if (activeStatus == 'rejected') return '$mediaText · 已拒绝';
    if (activeStatus == 'ended') return '$mediaText · 已挂断';
    return '$mediaText · $activeStatus';
  }

  void showSettingsSheet() {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => Padding(
        padding: EdgeInsets.fromLTRB(16, 0, 16, 16 + MediaQuery.of(context).viewInsets.bottom),
        child: ListView(
          shrinkWrap: true,
          children: [
            Text('连接设置', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
            const SizedBox(height: 14),
            _Field(label: '服务器', controller: hostController),
            Row(
              children: [
                Expanded(child: _Field(label: 'HTTP', controller: httpPortController, keyboardType: TextInputType.number)),
                const SizedBox(width: 10),
                Expanded(child: _Field(label: 'TCP', controller: tcpPortController, keyboardType: TextInputType.number)),
              ],
            ),
            Row(
              children: [
                Expanded(child: _Field(label: '当前用户', controller: userIdController)),
                const SizedBox(width: 10),
                Expanded(child: _Field(label: '对方用户', controller: peerIdController)),
              ],
            ),
            _Field(label: '会话 ID', controller: conversationIdController),
            _Field(label: 'Token', controller: tokenController),
            const SizedBox(height: 8),
            FilledButton.icon(
              onPressed: () {
                Navigator.pop(context);
                if (!connected) {
                  unawaited(connectSocket());
                } else {
                  sendAuth();
                }
                setState(() {});
              },
              icon: const Icon(Icons.login),
              label: const Text('保存并连接'),
            ),
          ],
        ),
      ),
    );
  }

  void showDebugSheet() {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => SizedBox(
        height: MediaQuery.of(context).size.height * 0.72,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('调试信息', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              Text('HTTP $baseUrl · TCP ${tcpPortController.text.trim()} · SIP $sipStatus'),
              if (mediaConfig?['selfSipUri'] != null) Text('SIP URI ${mediaConfig!['selfSipUri']}'),
              const SizedBox(height: 10),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  OutlinedButton.icon(onPressed: loadCallReadiness, icon: const Icon(Icons.fact_check), label: const Text('检测')),
                  OutlinedButton.icon(onPressed: loadCalls, icon: const Icon(Icons.history), label: const Text('记录')),
                  OutlinedButton.icon(onPressed: connected ? sendAuth : connectSocket, icon: const Icon(Icons.login), label: const Text('认证')),
                ],
              ),
              const Divider(height: 24),
              Expanded(
                child: ListView.builder(
                  reverse: true,
                  itemCount: logs.length,
                  itemBuilder: (context, index) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 5),
                    child: SelectableText(logs[index], style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PeerHeader extends StatelessWidget {
  const _PeerHeader({
    required this.peerId,
    required this.statusText,
    required this.statusColor,
    required this.callText,
    required this.onAudio,
    required this.onVideo,
    required this.onHangup,
    required this.onDebug,
  });

  final String peerId;
  final String statusText;
  final Color statusColor;
  final String callText;
  final VoidCallback? onAudio;
  final VoidCallback? onVideo;
  final VoidCallback? onHangup;
  final VoidCallback onDebug;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colorScheme.surface,
        border: Border(bottom: BorderSide(color: colorScheme.outlineVariant)),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 12, 12),
        child: Row(
          children: [
            Stack(
              children: [
                CircleAvatar(
                  radius: 24,
                  backgroundColor: const Color(0xFFEFF6FF),
                  child: Text(peerId.isEmpty ? 'U' : peerId.substring(0, 1).toUpperCase(), style: const TextStyle(fontWeight: FontWeight.w800)),
                ),
                Positioned(
                  right: 1,
                  bottom: 1,
                  child: DecoratedBox(
                    decoration: BoxDecoration(color: statusColor, shape: BoxShape.circle, border: Border.all(color: Colors.white, width: 2)),
                    child: const SizedBox(width: 12, height: 12),
                  ),
                ),
              ],
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('桌面端 $peerId', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                  const SizedBox(height: 2),
                  Text('$statusText · $callText', maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: colorScheme.onSurfaceVariant)),
                ],
              ),
            ),
            IconButton.filledTonal(onPressed: onAudio, tooltip: '语音', icon: const Icon(Icons.call)),
            const SizedBox(width: 6),
            IconButton.filledTonal(onPressed: onVideo, tooltip: '视频', icon: const Icon(Icons.videocam)),
            if (onHangup != null) ...[
              const SizedBox(width: 6),
              IconButton.filled(onPressed: onHangup, tooltip: '挂断', icon: const Icon(Icons.call_end)),
            ],
            IconButton(onPressed: onDebug, tooltip: '调试', icon: const Icon(Icons.bug_report_outlined)),
          ],
        ),
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({required this.entry});

  final ChatEntry entry;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    if (entry.direction == 'system') {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Center(
          child: DecoratedBox(
            decoration: BoxDecoration(color: const Color(0xFFE2E8F0), borderRadius: BorderRadius.circular(8)),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
              child: Text(entry.content, style: Theme.of(context).textTheme.bodySmall),
            ),
          ),
        ),
      );
    }

    return Align(
      alignment: entry.mine ? Alignment.centerRight : Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.76),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 5),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: entry.mine ? colorScheme.primary : Colors.white,
              borderRadius: BorderRadius.circular(8),
              border: entry.mine ? null : Border.all(color: colorScheme.outlineVariant),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
              child: Text(
                entry.content,
                style: TextStyle(color: entry.mine ? colorScheme.onPrimary : colorScheme.onSurface, height: 1.35),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _CallPage extends StatelessWidget {
  const _CallPage({
    required this.call,
    required this.peerId,
    required this.sipStatus,
    required this.cameraController,
    required this.cameraInitFuture,
    required this.cameraStatus,
    required this.onAnswer,
    required this.onReject,
    required this.onHangup,
    required this.onDebug,
  });

  final Map<String, Object?> call;
  final String peerId;
  final String sipStatus;
  final CameraController? cameraController;
  final Future<void>? cameraInitFuture;
  final String cameraStatus;
  final VoidCallback? onAnswer;
  final VoidCallback? onReject;
  final VoidCallback? onHangup;
  final VoidCallback onDebug;

  @override
  Widget build(BuildContext context) {
    final isVideo = call['mediaType']?.toString() == 'video';
    final status = call['status']?.toString() ?? '';
    final isRinging = status == 'ringing';
    final isIncoming = onAnswer != null;
    final title = isVideo ? '视频通话' : '语音通话';
    final stateText = isRinging
        ? (isIncoming ? '$peerId 来电' : '正在呼叫 $peerId')
        : '与 $peerId 通话中';
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        foregroundColor: Colors.white,
        title: Text(title),
        actions: [IconButton(onPressed: onDebug, icon: const Icon(Icons.bug_report_outlined))],
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(18, 12, 18, 0),
                child: isVideo ? _VideoCallStage(
                  cameraController: cameraController,
                  cameraInitFuture: cameraInitFuture,
                  cameraStatus: cameraStatus,
                  remoteEnabled: Platform.isAndroid,
                ) : _AudioCallStage(peerId: peerId),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 12, 24, 6),
              child: Column(
                children: [
                  Text(stateText, textAlign: TextAlign.center, style: Theme.of(context).textTheme.headlineSmall?.copyWith(color: Colors.white, fontWeight: FontWeight.w700)),
                  const SizedBox(height: 8),
                  Text(
                    isVideo ? 'SIP $sipStatus · 本机摄像头预览' : 'SIP $sipStatus',
                    style: const TextStyle(color: Color(0xFFCBD5E1), fontSize: 15),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 12, 24, 28),
              child: Row(
                children: [
                  if (onReject != null) ...[
                    Expanded(
                      child: FilledButton.icon(
                        style: FilledButton.styleFrom(backgroundColor: const Color(0xFFDC2626), minimumSize: const Size.fromHeight(54)),
                        onPressed: onReject,
                        icon: const Icon(Icons.call_end),
                        label: const Text('拒绝'),
                      ),
                    ),
                    const SizedBox(width: 14),
                  ],
                  if (onAnswer != null) ...[
                    Expanded(
                      child: FilledButton.icon(
                        style: FilledButton.styleFrom(backgroundColor: const Color(0xFF16A34A), minimumSize: const Size.fromHeight(54)),
                        onPressed: onAnswer,
                        icon: const Icon(Icons.call),
                        label: const Text('接听'),
                      ),
                    ),
                  ] else ...[
                    Expanded(
                      child: FilledButton.icon(
                        style: FilledButton.styleFrom(backgroundColor: const Color(0xFFDC2626), minimumSize: const Size.fromHeight(54)),
                        onPressed: onHangup,
                        icon: const Icon(Icons.call_end),
                        label: Text(isRinging ? '取消' : '挂断'),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _VideoCallStage extends StatelessWidget {
  const _VideoCallStage({
    required this.cameraController,
    required this.cameraInitFuture,
    required this.cameraStatus,
    required this.remoteEnabled,
  });

  final CameraController? cameraController;
  final Future<void>? cameraInitFuture;
  final String cameraStatus;
  final bool remoteEnabled;

  @override
  Widget build(BuildContext context) {
    final controller = cameraController;
    final future = cameraInitFuture;
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: ColoredBox(
        color: Colors.black,
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (remoteEnabled)
              const AndroidView(viewType: 'enterprise_im/pjsip_video_view')
            else
              const Center(child: Text('当前平台不支持原生远端视频窗口', style: TextStyle(color: Color(0xFFCBD5E1), fontSize: 16))),
            Positioned(
              right: 12,
              top: 12,
              width: 120,
              height: 170,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(10),
                child: ColoredBox(
                  color: const Color(0xFF111827),
                  child: controller != null && future != null
                    ? FutureBuilder<void>(
                        future: future,
                        builder: (context, snapshot) {
                          if (snapshot.connectionState == ConnectionState.done && controller.value.isInitialized) {
                            return FittedBox(
                              fit: BoxFit.cover,
                              child: SizedBox(
                                width: controller.value.previewSize?.height ?? 720,
                                height: controller.value.previewSize?.width ?? 1280,
                                child: CameraPreview(controller),
                              ),
                            );
                          }
                          return const Center(child: CircularProgressIndicator(color: Colors.white));
                        },
                      )
                    : Center(
                        child: Text(
                          cameraStatus == 'no_camera' ? '无摄像头' : '摄像头',
                          style: const TextStyle(color: Color(0xFFCBD5E1), fontSize: 12),
                        ),
                      ),
                ),
              ),
            ),
            Positioned(
              left: 12,
              right: 12,
              bottom: 12,
              child: DecoratedBox(
                decoration: BoxDecoration(color: Colors.black.withAlpha(115), borderRadius: BorderRadius.circular(8)),
                child: const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  child: Text('主画面：远端视频；右上角：本机预览', textAlign: TextAlign.center, style: TextStyle(color: Colors.white)),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AudioCallStage extends StatelessWidget {
  const _AudioCallStage({required this.peerId});

  final String peerId;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircleAvatar(
            radius: 54,
            backgroundColor: const Color(0xFF1D4ED8),
            child: Text(peerId.isEmpty ? 'U' : peerId.substring(0, 1).toUpperCase(), style: const TextStyle(color: Colors.white, fontSize: 40, fontWeight: FontWeight.w800)),
          ),
          const SizedBox(height: 24),
          const Icon(Icons.graphic_eq, color: Color(0xFF93C5FD), size: 56),
        ],
      ),
    );
  }
}

class _OutgoingCallBar extends StatelessWidget {
  const _OutgoingCallBar({required this.mediaType, required this.onHangup});

  final String mediaType;
  final VoidCallback onHangup;

  @override
  Widget build(BuildContext context) {
    final text = mediaType == 'video' ? '视频呼叫中，等待对方接听' : '语音呼叫中，等待对方接听';
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 0),
      child: DecoratedBox(
        decoration: BoxDecoration(color: const Color(0xFFFFFBEB), borderRadius: BorderRadius.circular(8)),
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Row(
            children: [
              const Icon(Icons.call_made, color: Color(0xFFD97706)),
              const SizedBox(width: 8),
              Expanded(child: Text(text)),
              FilledButton.tonalIcon(onPressed: onHangup, icon: const Icon(Icons.call_end), label: const Text('取消')),
            ],
          ),
        ),
      ),
    );
  }
}

class _IncomingCallBar extends StatelessWidget {
  const _IncomingCallBar({
    required this.mediaType,
    required this.callerId,
    required this.onAnswer,
    required this.onReject,
  });

  final String mediaType;
  final String callerId;
  final VoidCallback onAnswer;
  final VoidCallback onReject;

  @override
  Widget build(BuildContext context) {
    final text = mediaType == 'video' ? '$callerId 发起视频通话' : '$callerId 发起语音通话';
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 10),
      child: DecoratedBox(
        decoration: BoxDecoration(color: const Color(0xFFEFF6FF), borderRadius: BorderRadius.circular(8)),
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Row(
            children: [
              const Icon(Icons.call_received, color: Color(0xFF2563EB)),
              const SizedBox(width: 8),
              Expanded(child: Text(text)),
              OutlinedButton(onPressed: onReject, child: const Text('拒绝')),
              const SizedBox(width: 8),
              FilledButton(onPressed: onAnswer, child: const Text('接听')),
            ],
          ),
        ),
      ),
    );
  }
}

class _Field extends StatelessWidget {
  const _Field({required this.label, required this.controller, this.keyboardType});

  final String label;
  final TextEditingController controller;
  final TextInputType? keyboardType;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: TextField(
        controller: controller,
        keyboardType: keyboardType,
        decoration: InputDecoration(
          border: const OutlineInputBorder(),
          labelText: label,
          isDense: true,
        ),
      ),
    );
  }
}
