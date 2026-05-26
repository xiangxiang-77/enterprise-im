import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:camera/camera.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image/image.dart' as img;
import 'package:record/record.dart';

import '../models/message.dart';
import '../models/session.dart';
import '../models/user.dart';
import '../services/api_service.dart';
import '../services/socket_service.dart';
import '../services/storage_service.dart';
import '../widgets/message_bubble.dart';

class ChatScreen extends StatefulWidget {
  const ChatScreen({
    super.key,
    required this.session,
    required this.apiService,
    required this.socketService,
    required this.storageService,
    required this.currentUser,
    required this.onBack,
  });

  final Session session;
  final ApiService apiService;
  final SocketService socketService;
  final StorageService storageService;
  final AppUser currentUser;
  final VoidCallback onBack;

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> with WidgetsBindingObserver {
  static const sipChannel = MethodChannel('enterprise_im/sip');

  final messageController = TextEditingController();
  final scrollController = ScrollController();

  final List<Message> messages = [];
  final List<String> logs = [];

  // Call state
  bool callLoading = false;
  Map<String, Object?>? activeCall;
  Map<String, Object?>? readiness;
  Map<String, Object?>? mediaConfig;
  List<Map<String, Object?>> calls = [];
  String sipStatus = 'idle';
  String? nativeCallId;
  String? nativeStartingCallId;
  String? lastMessageId;
  Future<void>? nativeStartFuture;
  final Set<String> nativeFailedCallIds = <String>{};
  CameraController? cameraController;
  Future<void>? cameraInitFuture;
  String cameraStatus = 'idle';

  // Message ops state
  Message? selectedMessage;
  bool multiSelectMode = false;
  final Set<String> selectedMessageIds = <String>{};
  double? uploadProgress;
  late bool muted;
  late bool pinned;
  late bool screenshotNotice;
  late bool recallNotice;
  late bool readAfterBurn;
  late bool strongReminder;
  late bool displayMemberNicknames;
  late bool savedToContacts;
  bool conversationSettingsSaving = false;
  bool uploadQueueResuming = false;

  String? _typingPeer;
  Timer? _typingTimer;

  int _groupOnlineCount = 0;
  int _groupMemberCount = 0;
  Timer? _onlinePollTimer;

  final _audioRecorder = AudioRecorder();
  bool _isRecording = false;

  @override
  void initState() {
    super.initState();
    muted = widget.session.muted;
    pinned = widget.session.pinned;
    screenshotNotice = widget.session.screenshotNotice;
    recallNotice = widget.session.recallNotice;
    readAfterBurn = widget.session.readAfterBurn;
    strongReminder = widget.session.strongReminder;
    displayMemberNicknames = widget.session.displayMemberNicknames;
    savedToContacts = widget.session.savedToContacts;
    WidgetsBinding.instance.addObserver(this);
    sipChannel.setMethodCallHandler(handleSipChannelCall);
    widget.socketService.onFrame = _handleSocketFrame;
    _loadLocalMessages();
    _loadServerMessages();
    _loadCallReadiness();
    unawaited(_resumePendingUploads());
    _uploadNativeDiagnostics('chat_open');
    if (widget.session.isGroup) {
      _pollGroupOnlineStatus();
      _onlinePollTimer = Timer.periodic(const Duration(seconds: 30), (_) => _pollGroupOnlineStatus());
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    sipChannel.setMethodCallHandler(null);
    widget.socketService.onFrame = null;
    messageController.dispose();
    scrollController.dispose();
    cameraController?.dispose();
    _typingTimer?.cancel();
    _onlinePollTimer?.cancel();
    super.dispose();
  }

  void _handleSocketFrame(Map<String, Object?> frame) {
    final type = frame['type']?.toString();
    final payload = frame['payload'];

    if (type == 'AUTH_OK' || type == 'AUTH_FAILED') {
      if (mounted) setState(() {});
      unawaited(refreshCalls());
      return;
    }
    if (type == 'ACK' && payload is Map) {
      final messageId = payload['messageId']?.toString();
      if (mounted) {
        setState(() {
          if (messageId != null && messageId.isNotEmpty) {
            lastMessageId = messageId;
            _addLog('LAST MESSAGE $messageId');
          }
        });
      }
      return;
    }
    if ((type == 'CALL_INVITE' || type == 'CALL_UPDATE') && payload is Map) {
      unawaited(_applyCallUpdate(Map<String, Object?>.from(payload), 'TCP $type'));
      return;
    }
    if (type == 'TYPING_DELIVER' && payload is Map) {
      final from = frame['from']?.toString() ?? '';
      final isTyping = payload['isTyping'] == true;
      if (mounted) {
        setState(() {
          _typingPeer = isTyping ? from : null;
        });
        _typingTimer?.cancel();
        if (isTyping) {
          _typingTimer = Timer(const Duration(seconds: 4), () {
            if (mounted) setState(() => _typingPeer = null);
          });
        }
      }
      return;
    }
    if (type == 'TEXT_DELIVER' && payload is Map) {
      final content = payload['content']?.toString() ?? '';
      final messageId = payload['messageId']?.toString();
      final sender = frame['from']?.toString() ?? widget.session.peerId ?? '';
      unawaited(widget.storageService.saveMessage(
        conversationId: frame['conversationId']?.toString(),
        sender: sender,
        content: content,
        direction: 'in',
      ));
      if (mounted) {
        setState(() {
          if (messageId != null && messageId.isNotEmpty) lastMessageId = messageId;
          messages.add(Message(
            sender: sender,
            content: content,
            direction: 'in',
            createdAt: DateTime.now(),
            serverId: messageId,
            conversationId: frame['conversationId']?.toString(),
          ));
        });
        _scrollToBottom();
      }
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(refreshCalls());
    }
  }

  // --- Sip Channel ---

  Future<void> handleSipChannelCall(MethodCall call) async {
    if (call.method != 'sipEvent') return;
    final args = call.arguments;
    final event = args is Map ? Map<Object?, Object?>.from(args) : <Object?, Object?>{};
    final type = event['type']?.toString() ?? 'event';
    final message = event['message']?.toString() ?? '';
    if (!mounted) return;
    setState(() {
      if (type == 'error') {
        sipStatus = message.isEmpty ? 'error' : 'error: ${_shortSipMessage(message)}';
      } else if (type == 'media' || type == 'video' || type == 'state' || type == 'registration' || type == 'call') {
        sipStatus = message.isEmpty ? type : _shortSipMessage(message);
      }
      _addLog('SIP EVENT $type $message');
    });
  }

  String _shortSipMessage(String message) {
    final compact = message.replaceAll(RegExp(r'\s+'), ' ').trim();
    if (compact.length <= 80) return compact;
    return '${compact.substring(0, 77)}...';
  }

  // --- Messages ---

  Future<void> _loadLocalMessages() async {
    final loaded = await widget.storageService.loadMessages(widget.session.id);
    if (mounted && loaded.isNotEmpty) {
      setState(() {
        messages
          ..clear()
          ..addAll(loaded);
      });
    }
  }

  Future<void> _loadServerMessages() async {
    try {
      final data = await widget.apiService.getMessages(widget.session.id);
      final items = _dataList(data);
      if (!mounted) return;
      final serverMessages = items.map((item) => Message.fromJson(_withFilePreview(item))).toList();
      if (serverMessages.isNotEmpty) {
        setState(() {
          messages
            ..clear()
            ..addAll(serverMessages);
          if (messages.isNotEmpty) {
            lastMessageId = messages.last.serverId;
          }
        });
      }
    } catch (error) {
      _addLog('LOAD MESSAGES ERROR $error');
    }
  }

  List<Map<String, Object?>> _dataList(Map<String, Object?> data) {
    final items = data['items'];
    if (items is List) {
      return items.whereType<Map>().map((item) => Map<String, Object?>.from(item)).toList();
    }
    return [];
  }

  Map<String, Object?> _withFilePreview(Map<String, Object?> item) {
    final copy = Map<String, Object?>.from(item);
    final fileId = copy['fileId']?.toString();
    if (fileId != null && fileId.isNotEmpty) {
      copy['fileUrl'] ??= '${widget.apiService.baseUrl}/api/files/$fileId/preview';
      copy['fileName'] ??= _nameFromRichContent(copy['content']?.toString() ?? '');
    }
    return copy;
  }

  String _nameFromRichContent(String content) {
    final match = RegExp(r'^\[[^\]]+\]\s*(.+)$').firstMatch(content);
    return match?.group(1)?.trim().isNotEmpty == true ? match!.group(1)!.trim() : content;
  }

  void _sendText() {
    final content = messageController.text.trim();
    if (content.isEmpty) return;

    final peerId = widget.session.peerId ?? '';

    widget.socketService.sendText(
      to: peerId,
      conversationId: widget.session.id,
      content: content,
    );

    messageController.clear();
    unawaited(widget.storageService.saveMessage(
      conversationId: widget.session.id,
      sender: widget.currentUser.id,
      content: content,
      direction: 'out',
    ));
    setState(() {
      messages.add(Message(
        sender: widget.currentUser.id,
        content: content,
        direction: 'out',
        createdAt: DateTime.now(),
        conversationId: widget.session.id,
      ));
    });
    _scrollToBottom();
  }

  Future<void> _pickAndSendFile() async {
    String? queuedPath;
    String? queuedName;
    try {
      final result = await FilePicker.platform.pickFiles(withData: false);
      final pickedPath = result?.files.single.path;
      if (pickedPath == null || pickedPath.isEmpty) return;
      queuedPath = pickedPath;
      queuedName = result!.files.single.name;

      final pickedFile = File(pickedPath);
      final originalName = queuedName;
      final size = await pickedFile.length();
      _addLog('FILE UPLOAD START $originalName $size');

      setState(() => uploadProgress = 0);
      final uploaded = await widget.apiService.uploadFileWithProgress(
        pickedFile,
        conversationId: widget.session.id,
        onProgress: (sent, total) {
          if (!mounted || total <= 0) return;
          setState(() => uploadProgress = sent / total);
        },
      );
      final fileId = uploaded['id']?.toString() ?? uploaded['fileId']?.toString() ?? '';
      final fileUrl = uploaded['previewUrl']?.toString() ??
          uploaded['downloadUrl']?.toString() ??
          uploaded['url']?.toString() ??
          uploaded['path']?.toString();
      final resolvedFileUrl = _resolveApiUrl(fileUrl);
      final fileName = uploaded['originalName']?.toString() ??
          uploaded['name']?.toString() ??
          uploaded['filename']?.toString() ??
          originalName;
      final uploadedSize = int.tryParse(uploaded['sizeBytes']?.toString() ?? '') ?? size;
      final messageType = _richMessageType(fileName);
      final content = messageType == 'image' ? '[图片] $fileName' : '[文件] $fileName';

      widget.socketService.sendFrame(
        'TEXT',
        to: widget.session.peerId ?? '',
        conversationId: widget.session.id,
        payload: {
          'content': content,
          'fileId': fileId,
          'fileUrl': resolvedFileUrl,
          'fileName': fileName,
          'fileSize': uploadedSize,
          'messageType': messageType,
        },
      );

      await widget.storageService.saveMessage(
        conversationId: widget.session.id,
        sender: widget.currentUser.id,
        content: content,
        direction: 'out',
        type: messageType,
        fileUrl: resolvedFileUrl,
        fileName: fileName,
        fileSize: uploadedSize,
      );
      if (!mounted) return;
      setState(() {
        messages.add(Message(
          sender: widget.currentUser.id,
          content: content,
          direction: 'out',
          createdAt: DateTime.now(),
          conversationId: widget.session.id,
          type: messageType,
          fileUrl: resolvedFileUrl,
          fileName: fileName,
          fileSize: uploadedSize,
        ));
      });
      _scrollToBottom();
      _addLog('FILE UPLOAD SENT $fileId $fileName');
    } catch (error) {
      _addLog('FILE UPLOAD ERROR $error');
      if (queuedPath != null && queuedName != null) {
        await widget.storageService.enqueueUpload(
          conversationId: widget.session.id,
          peerId: widget.session.peerId,
          filePath: queuedPath,
          originalName: queuedName,
          error: error.toString(),
        );
        _addLog('FILE UPLOAD QUEUED $queuedName');
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('文件发送失败: $error')));
      }
    } finally {
      if (mounted) {
        setState(() => uploadProgress = null);
      }
    }
  }

  String _richMessageType(String fileName) {
    final lower = fileName.toLowerCase();
    if (lower.endsWith('.png') || lower.endsWith('.jpg') || lower.endsWith('.jpeg') || lower.endsWith('.gif') || lower.endsWith('.webp')) {
      return 'image';
    }
    if (lower.endsWith('.mp4') || lower.endsWith('.mov') || lower.endsWith('.m4v') || lower.endsWith('.webm')) {
      return 'video';
    }
    return 'file';
  }

  String? _resolveApiUrl(String? value) {
    if (value == null || value.isEmpty) return value;
    if (value.startsWith('http://') || value.startsWith('https://')) return value;
    if (value.startsWith('/')) return '${widget.apiService.baseUrl}$value';
    return '${widget.apiService.baseUrl}/$value';
  }

  Future<void> _resumePendingUploads() async {
    if (uploadQueueResuming) return;
    uploadQueueResuming = true;
    try {
      final pending = await widget.storageService.loadPendingUploads(widget.session.id);
      if (pending.isEmpty) return;
      _addLog('UPLOAD QUEUE RESUME ${pending.length}');
      for (final item in pending) {
        final id = item['id'] is int ? item['id'] as int : int.tryParse(item['id']?.toString() ?? '') ?? -1;
        final filePath = item['file_path']?.toString() ?? '';
        final originalName = item['original_name']?.toString() ?? filePath.split(Platform.pathSeparator).last;
        final retryCount = item['retry_count'] is int ? item['retry_count'] as int : int.tryParse(item['retry_count']?.toString() ?? '0') ?? 0;
        if (id < 0 || filePath.isEmpty) continue;
        final file = File(filePath);
        if (!await file.exists()) {
          await widget.storageService.deleteUploadQueueItem(id);
          _addLog('UPLOAD QUEUE DROP missing $filePath');
          continue;
        }
        try {
          await _sendPreparedFile(file, originalName);
          await widget.storageService.deleteUploadQueueItem(id);
          _addLog('UPLOAD QUEUE DONE $originalName');
        } catch (error) {
          await widget.storageService.markUploadQueued(id, error: error.toString(), retryCount: retryCount + 1);
          _addLog('UPLOAD QUEUE RETRY LATER $originalName $error');
          break;
        }
      }
    } finally {
      uploadQueueResuming = false;
    }
  }

  Future<void> _startRecording() async {
    if (_isRecording) return;
    final hasPermission = await _audioRecorder.hasPermission();
    if (!hasPermission) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('需要麦克风权限')));
      }
      return;
    }
    try {
      final filePath = '${Directory.systemTemp.path}/voice_${DateTime.now().millisecondsSinceEpoch}.m4a';
      await _audioRecorder.start(const RecordConfig(encoder: AudioEncoder.aacLc), path: filePath);
      setState(() => _isRecording = true);
    } catch (error) {
      _addLog('RECORD START ERROR $error');
    }
  }

  Future<void> _stopRecordingAndSend() async {
    if (!_isRecording) return;
    try {
      final path = await _audioRecorder.stop();
      setState(() => _isRecording = false);
      if (path == null) return;
      final file = File(path);
      final size = await file.length();
      setState(() => uploadProgress = 0);
      final uploaded = await widget.apiService.uploadFileWithProgress(
        file,
        conversationId: widget.session.id,
        onProgress: (sent, total) {
          if (mounted && total > 0) setState(() => uploadProgress = sent / total);
        },
      );
      final fileId = uploaded['id']?.toString() ?? '';
      final fileUrl = _resolveApiUrl((uploaded['previewUrl'] ?? uploaded['downloadUrl'] ?? uploaded['url']) as String?);
      final content = '[语音] ${uploaded['originalName'] ?? 'voice.m4a'}';
      widget.socketService.sendFrame(
        'TEXT',
        to: widget.session.peerId ?? '',
        conversationId: widget.session.id,
        payload: {
          'content': content,
          'fileId': fileId,
          'fileUrl': fileUrl,
          'fileName': 'voice.m4a',
          'fileSize': size,
          'messageType': 'voice',
          'voiceDuration': uploaded['duration'] ?? 0,
        },
      );
      if (mounted) {
        setState(() {
          messages.add(Message(
            sender: widget.currentUser.id,
            content: content,
            direction: 'out',
            createdAt: DateTime.now(),
            conversationId: widget.session.id,
            type: 'voice',
            fileUrl: fileUrl,
            fileName: 'voice.m4a',
            fileSize: size,
          ));
        });
      }
      _scrollToBottom();
    } catch (error) {
      _addLog('RECORD SEND ERROR $error');
      setState(() => _isRecording = false);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('发送语音失败: $error')));
      }
    } finally {
      if (mounted) setState(() => uploadProgress = null);
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (scrollController.hasClients) {
        scrollController.animateTo(
          scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOut,
        );
      }
    });
  }

  // --- Message Operations ---

  Future<void> _favoriteMessage(Message msg) async {
    final id = msg.serverId;
    if (id == null || id.isEmpty) {
      _addLog('MESSAGE OP no server message id');
      return;
    }
    try {
      await widget.apiService.favoriteMessage(id);
      _addLog('MESSAGE FAVORITE $id');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已收藏')));
      }
    } catch (error) {
      _addLog('MESSAGE FAVORITE ERROR $error');
    }
  }

  Future<void> _likeMessage(Message msg) async {
    final id = msg.serverId;
    if (id == null || id.isEmpty) {
      _addLog('MESSAGE OP no server message id');
      return;
    }
    try {
      await widget.apiService.likeMessage(id);
      _addLog('MESSAGE LIKE $id');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已点赞')));
      }
    } catch (error) {
      _addLog('MESSAGE LIKE ERROR $error');
    }
  }

  Future<void> _recallMessage(Message msg) async {
    final id = msg.serverId;
    if (id == null || id.isEmpty) {
      _addLog('MESSAGE OP no server message id');
      return;
    }
    try {
      await widget.apiService.recallMessage(id);
      _addLog('MESSAGE RECALL $id');
      setState(() {
        msg.recalled = true;
        msg.content = '消息已撤回';
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('消息已撤回')));
      }
    } catch (error) {
      _addLog('MESSAGE RECALL ERROR $error');
    }
  }

  Future<void> _editMessage(Message msg, String newContent) async {
    final id = msg.serverId;
    if (id == null || id.isEmpty || newContent.isEmpty) return;
    try {
      await widget.apiService.editMessage(id, newContent);
      _addLog('MESSAGE EDIT $id');
      setState(() {
        msg.content = newContent;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('消息已编辑')));
      }
    } catch (error) {
      _addLog('MESSAGE EDIT ERROR $error');
    }
  }

  void _showMessageActions(Message msg) {
    setState(() => selectedMessage = msg);
    showModalBottomSheet<void>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (msg.serverId != null) ...[
              ListTile(
                leading: const Icon(Icons.checklist),
                title: const Text('多选'),
                onTap: () {
                  Navigator.pop(context);
                  _toggleMessageSelection(msg);
                },
              ),
              ListTile(
                leading: const Icon(Icons.forward),
                title: const Text('转发'),
                onTap: () {
                  Navigator.pop(context);
                  _showForwardDialog([msg.serverId!]);
                },
              ),
              ListTile(
                leading: const Icon(Icons.done_all),
                title: const Text('已读明细'),
                onTap: () {
                  Navigator.pop(context);
                  _showReadStatus(msg.serverId!);
                },
              ),
              ListTile(
                leading: const Icon(Icons.star_border),
                title: const Text('收藏'),
                onTap: () {
                  Navigator.pop(context);
                  unawaited(_favoriteMessage(msg));
                },
              ),
              ListTile(
                leading: const Icon(Icons.thumb_up_alt_outlined),
                title: const Text('点赞'),
                onTap: () {
                  Navigator.pop(context);
                  unawaited(_likeMessage(msg));
                },
              ),
            ],
            if (msg.mine && msg.serverId != null) ...[
              ListTile(
                leading: const Icon(Icons.undo),
                title: const Text('撤回'),
                onTap: () {
                  Navigator.pop(context);
                  unawaited(_recallMessage(msg));
                },
              ),
              ListTile(
                leading: const Icon(Icons.edit),
                title: const Text('编辑'),
                onTap: () {
                  Navigator.pop(context);
                  _showEditDialog(msg);
                },
              ),
            ],
            if (msg.type == 'image' && msg.fileUrl != null)
              ListTile(
                leading: const Icon(Icons.tune),
                title: const Text('编辑图片后发送'),
                onTap: () {
                  Navigator.pop(context);
                  _showImageEditDialog(msg);
                },
              ),
            ListTile(
              leading: const Icon(Icons.copy),
              title: const Text('复制'),
              onTap: () {
                Navigator.pop(context);
                Clipboard.setData(ClipboardData(text: msg.content));
                ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已复制')));
              },
            ),
          ],
        ),
      ),
    );
  }

  void _showEditDialog(Message msg) {
    final editController = TextEditingController(text: msg.content);
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('编辑消息'),
        content: TextField(
          controller: editController,
          maxLines: null,
          decoration: const InputDecoration(border: OutlineInputBorder()),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              unawaited(_editMessage(msg, editController.text.trim()));
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );
  }

  void _toggleMessageSelection(Message msg) {
    final id = msg.serverId;
    if (id == null || id.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('只能选择服务端消息')));
      return;
    }
    setState(() {
      multiSelectMode = true;
      if (selectedMessageIds.contains(id)) {
        selectedMessageIds.remove(id);
      } else {
        selectedMessageIds.add(id);
      }
      if (selectedMessageIds.isEmpty) {
        multiSelectMode = false;
      }
    });
  }

  Future<void> _showForwardDialog(List<String> messageIds) async {
    final targetController = TextEditingController(text: widget.session.id);
    var mode = messageIds.length > 1 ? 'combine' : 'single';
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('转发消息'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: targetController,
                decoration: const InputDecoration(
                  labelText: '目标会话ID，多个用逗号分隔',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'single', label: Text('逐条')),
                  ButtonSegment(value: 'combine', label: Text('合并')),
                ],
                selected: {mode},
                onSelectionChanged: (value) => setDialogState(() => mode = value.first),
              ),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('转发')),
          ],
        ),
      ),
    );
    if (confirmed != true) return;
    final targets = targetController.text
        .split(',')
        .map((item) => item.trim())
        .where((item) => item.isNotEmpty)
        .toList();
    if (targets.isEmpty) return;
    try {
      await widget.apiService.forwardMessages(messageIds, targets, mode: mode);
      _addLog('MESSAGE FORWARD ${messageIds.length} -> ${targets.join(",")} $mode');
      if (!mounted) return;
      setState(() {
        multiSelectMode = false;
        selectedMessageIds.clear();
      });
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('转发成功')));
    } catch (error) {
      _addLog('MESSAGE FORWARD ERROR $error');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('转发失败: $error')));
      }
    }
  }

  Future<void> _showReadStatus(String messageId) async {
    try {
      final status = await widget.apiService.getMessageReadStatus(messageId);
      final read = _readMemberList(status['read']);
      final unread = _readMemberList(status['unread']);
      if (!mounted) return;
      showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('已读明细'),
          content: SizedBox(
            width: 340,
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('已读 ${read.length}', style: Theme.of(context).textTheme.titleSmall),
                  const SizedBox(height: 6),
                  if (read.isEmpty)
                    const Text('暂无', style: TextStyle(color: Colors.grey))
                  else
                    ...read.map((item) => ListTile(
                          dense: true,
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.check_circle_outline, size: 18),
                          title: Text(item['displayName'] ?? item['userId'] ?? ''),
                          subtitle: Text(item['readAt'] ?? ''),
                        )),
                  const Divider(),
                  Text('未读 ${unread.length}', style: Theme.of(context).textTheme.titleSmall),
                  const SizedBox(height: 6),
                  if (unread.isEmpty)
                    const Text('暂无', style: TextStyle(color: Colors.grey))
                  else
                    ...unread.map((item) => ListTile(
                          dense: true,
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.radio_button_unchecked, size: 18),
                          title: Text(item['displayName'] ?? item['userId'] ?? ''),
                        )),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('关闭')),
          ],
        ),
      );
    } catch (error) {
      _addLog('READ STATUS ERROR $error');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('已读明细失败: $error')));
      }
    }
  }

  List<Map<String, String>> _readMemberList(Object? value) {
    if (value is! List) return <Map<String, String>>[];
    return value
        .whereType<Map>()
        .map((item) => {
              'userId': item['userId']?.toString() ?? '',
              'displayName': item['displayName']?.toString() ?? '',
              'readAt': item['readAt']?.toString() ?? '',
            })
        .toList();
  }

  Future<void> _showImageEditDialog(Message msg) async {
    final sourceUrl = msg.fileUrl;
    if (sourceUrl == null || sourceUrl.isEmpty) return;
    var quarterTurns = 0;
    var grayscale = false;
    var cropCenter = false;
    var doodle = false;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('图片编辑'),
          content: SizedBox(
            width: 320,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox(
                  height: 220,
                  child: RotatedBox(
                    quarterTurns: quarterTurns,
                    child: ColorFiltered(
                      colorFilter: grayscale
                          ? const ColorFilter.matrix(<double>[
                              0.2126, 0.7152, 0.0722, 0, 0,
                              0.2126, 0.7152, 0.0722, 0, 0,
                              0.2126, 0.7152, 0.0722, 0, 0,
                              0, 0, 0, 1, 0,
                            ])
                          : const ColorFilter.mode(Colors.transparent, BlendMode.dst),
                      child: Image.network(sourceUrl, fit: BoxFit.contain),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  children: [
                    OutlinedButton.icon(
                      onPressed: () => setDialogState(() => quarterTurns = (quarterTurns + 1) % 4),
                      icon: const Icon(Icons.rotate_right),
                      label: const Text('旋转'),
                    ),
                    FilterChip(
                      selected: grayscale,
                      onSelected: (value) => setDialogState(() => grayscale = value),
                      label: const Text('灰度'),
                    ),
                    FilterChip(
                      selected: cropCenter,
                      onSelected: (value) => setDialogState(() => cropCenter = value),
                      label: const Text('中心裁剪'),
                    ),
                    FilterChip(
                      selected: doodle,
                      onSelected: (value) => setDialogState(() => doodle = value),
                      label: const Text('涂鸦线'),
                    ),
                  ],
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('发送副本')),
          ],
        ),
      ),
    );
    if (confirmed != true) return;
    try {
      final edited = await _buildEditedImageFile(sourceUrl, quarterTurns, grayscale, cropCenter, doodle);
      await _sendPreparedFile(edited, edited.uri.pathSegments.last);
    } catch (error) {
      _addLog('IMAGE EDIT ERROR $error');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('图片编辑失败: $error')));
      }
    }
  }

  Future<File> _buildEditedImageFile(String sourceUrl, int quarterTurns, bool grayscale, bool cropCenter, bool doodle) async {
    final client = HttpClient();
    try {
      final request = await client.getUrl(Uri.parse(sourceUrl));
      if (widget.apiService.token != null && widget.apiService.token!.isNotEmpty) {
        request.headers.set(HttpHeaders.authorizationHeader, 'Bearer ${widget.apiService.token}');
      }
      final response = await request.close();
      final bytes = await consolidateHttpClientResponseBytes(response);
      var image = img.decodeImage(bytes);
      if (image == null) throw 'unsupported image';
      if (quarterTurns % 4 != 0) {
        image = img.copyRotate(image, angle: 90 * (quarterTurns % 4));
      }
      if (cropCenter) {
        final side = image.width < image.height ? image.width : image.height;
        image = img.copyCrop(
          image,
          x: ((image.width - side) / 2).round(),
          y: ((image.height - side) / 2).round(),
          width: side,
          height: side,
        );
      }
      if (grayscale) {
        image = img.grayscale(image);
      }
      if (doodle) {
        final color = img.ColorRgb8(255, 0, 0);
        img.drawLine(image, x1: 12, y1: 12, x2: image.width - 12, y2: image.height - 12, color: color, thickness: 8);
        img.drawLine(image, x1: 12, y1: image.height - 12, x2: image.width - 12, y2: 12, color: color, thickness: 8);
      }
      final encoded = img.encodeJpg(image, quality: 92);
      final output = File('${Directory.systemTemp.path}/enterprise_im_edit_${DateTime.now().millisecondsSinceEpoch}.jpg');
      await output.writeAsBytes(encoded, flush: true);
      return output;
    } finally {
      client.close(force: true);
    }
  }

  Future<void> _sendPreparedFile(File file, String originalName) async {
    final size = await file.length();
    setState(() => uploadProgress = 0);
    try {
      final uploaded = await widget.apiService.uploadFileWithProgress(
        file,
        conversationId: widget.session.id,
        onProgress: (sent, total) {
          if (!mounted || total <= 0) return;
          setState(() => uploadProgress = sent / total);
        },
      );
      final fileId = uploaded['id']?.toString() ?? uploaded['fileId']?.toString() ?? '';
      final fileUrl = uploaded['previewUrl']?.toString() ??
          uploaded['downloadUrl']?.toString() ??
          uploaded['url']?.toString() ??
          uploaded['path']?.toString();
      final resolvedFileUrl = _resolveApiUrl(fileUrl);
      final fileName = uploaded['originalName']?.toString() ??
          uploaded['name']?.toString() ??
          uploaded['filename']?.toString() ??
          originalName;
      final uploadedSize = int.tryParse(uploaded['sizeBytes']?.toString() ?? '') ?? size;
      final messageType = _richMessageType(fileName);
      final content = messageType == 'image' ? '[图片] $fileName' : '[文件] $fileName';

      widget.socketService.sendFrame(
        'TEXT',
        to: widget.session.peerId ?? '',
        conversationId: widget.session.id,
        payload: {
          'content': content,
          'fileId': fileId,
          'fileUrl': resolvedFileUrl,
          'fileName': fileName,
          'fileSize': uploadedSize,
          'messageType': messageType,
        },
      );
      await widget.storageService.saveMessage(
        conversationId: widget.session.id,
        sender: widget.currentUser.id,
        content: content,
        direction: 'out',
        type: messageType,
        fileUrl: resolvedFileUrl,
        fileName: fileName,
        fileSize: uploadedSize,
      );
      if (!mounted) return;
      setState(() {
        messages.add(Message(
          sender: widget.currentUser.id,
          content: content,
          direction: 'out',
          createdAt: DateTime.now(),
          conversationId: widget.session.id,
          type: messageType,
          fileUrl: resolvedFileUrl,
          fileName: fileName,
          fileSize: uploadedSize,
        ));
      });
      _scrollToBottom();
      _addLog('IMAGE EDIT SENT $fileId $fileName');
    } finally {
      if (mounted) setState(() => uploadProgress = null);
    }
  }

  // --- Calls ---

  String get _selfId => widget.currentUser.id;
  String get _activeStatus => activeCall?['status']?.toString() ?? 'none';
  String get _activeCallerId => activeCall?['callerId']?.toString() ?? '';
  String get _activeCalleeId => activeCall?['calleeId']?.toString() ?? '';
  bool get _activeCallIsMine => _activeCallerId == _selfId;
  bool get _activeCallIsForMe => _activeCalleeId == _selfId;
  bool get _incomingRinging => _activeStatus == 'ringing' && _activeCallIsForMe && !_activeCallIsMine;
  bool get _outgoingRinging => _activeStatus == 'ringing' && _activeCallIsMine;
  bool get _activeCallVisible => _activeStatus == 'ringing' || _activeStatus == 'answered';

  String _callStatusText() {
    final ready = readiness?['ready'] == true;
    final mediaText = ready ? '音视频就绪' : '音视频检测中';
    if (_activeStatus == 'none') return mediaText;
    if (_incomingRinging) return '$mediaText · 来电';
    if (_outgoingRinging) return '$mediaText · 等待接听';
    if (_activeStatus == 'ringing') return '$mediaText · 呼叫中';
    if (_activeStatus == 'answered') return '$mediaText · 通话中';
    if (_activeStatus == 'rejected') return '$mediaText · 已拒绝';
    if (_activeStatus == 'ended') return '$mediaText · 已挂断';
    return '$mediaText · $_activeStatus';
  }

  String _peerIdForCall(Map<String, Object?> call) {
    final caller = call['callerId']?.toString() ?? widget.session.peerId ?? '';
    final callee = call['calleeId']?.toString() ?? widget.session.peerId ?? '';
    return caller == _selfId ? callee : caller;
  }

  Future<void> _loadCallReadiness() async {
    _runCallAction(() async {
      final data = await widget.apiService.getCallReadiness();
      readiness = data;
      _addLog('CALL READINESS ${jsonEncode(data)}');
    }, quiet: true);
  }

  Future<void> _startCall(String mediaType) async {
    final peerId = widget.session.peerId;
    if (peerId == null || peerId.isEmpty) {
      _addLog('CALL ERROR no peer for this session');
      return;
    }
    await _runCallAction(() async {
      activeCall = null;
      final data = await widget.apiService.startCall(
        callerId: _selfId,
        calleeId: peerId,
        conversationId: widget.session.id,
        mediaType: mediaType,
      );
      activeCall = data;
      await widget.storageService.saveCall(data);
      _addLog('CALL START ${jsonEncode(data)}');
      _syncCameraPreviewForCall(data);
      if (data['mediaStatus'] == 'media_ready') {
        unawaited(_startNativeSipForCall(data));
      }
      await refreshCalls();
      unawaited(_pollActiveCall(data['id']?.toString() ?? ''));
    });
  }

  Future<void> _answerCall() async {
    if (!_incomingRinging) {
      setState(() => _addLog('CALL ANSWER BLOCKED not incoming ringing'));
      return;
    }
    if (activeCall?['mediaStatus'] == 'media_ready') {
      await _startNativeSipForCall(activeCall!);
    }
    await _transitionCall('answer');
  }

  Future<void> _rejectCall() async {
    await _transitionCall('reject');
  }

  Future<void> _hangupCall() async {
    await _transitionCall('hangup');
  }

  Future<void> _transitionCall(String action) async {
    final callId = activeCall?['id']?.toString();
    if (callId == null || callId.isEmpty) {
      setState(() => _addLog('CALL ERROR no active call'));
      return;
    }
    if (action == 'reject' || action == 'hangup') {
      await _stopNativeSip();
      await _stopCameraPreview();
    }
    await _runCallAction(() async {
      final data = await widget.apiService.transitionCall(callId, action, _selfId);
      activeCall = data;
      await widget.storageService.saveCall(data);
      _addLog('CALL ${action.toUpperCase()} ${jsonEncode(data)}');
      _syncCameraPreviewForCall(data);
      if (action == 'answer' && data['mediaStatus'] == 'media_ready' && nativeCallId != callId) {
        await _startNativeSipForCall(data);
      }
      if (action == 'reject' || action == 'hangup') {
        await _stopNativeSip();
        await _stopCameraPreview();
      }
      await refreshCalls();
    });
  }

  Future<void> _startNativeSipForCall(Map<String, Object?> call) async {
    final callId = call['id']?.toString() ?? '';
    if (callId.isNotEmpty && nativeCallId == callId) {
      _addLog('SIP already started for $callId');
      return;
    }
    if (callId.isNotEmpty && nativeFailedCallIds.contains(callId)) {
      _addLog('SIP start skipped after previous failure for $callId');
      return;
    }
    if (callId.isNotEmpty && nativeStartingCallId == callId && nativeStartFuture != null) {
      _addLog('SIP start already running for $callId');
      await nativeStartFuture;
      return;
    }
    nativeStartingCallId = callId;
    final startFuture = _doStartNativeSipForCall(call);
    nativeStartFuture = startFuture;
    try {
      await startFuture;
    } finally {
      if (nativeStartingCallId == callId) {
        nativeStartingCallId = null;
        nativeStartFuture = null;
      }
    }
  }

  Future<void> _doStartNativeSipForCall(Map<String, Object?> call) async {
    final callId = call['id']?.toString() ?? '';
    final peerId = _peerIdForCall(call);
    final outbound = call['callerId']?.toString() == _selfId;
    final config = await widget.apiService.getMediaConfig(
      userId: _selfId,
      calleeId: peerId,
      platform: 'android',
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
      final status = result?['status']?.toString() ?? 'unknown';
      final sipStarted = status != 'permission_required' &&
          status != 'error' &&
          !status.startsWith('error');
      setState(() {
        sipStatus = status;
        nativeCallId = sipStarted ? callId : null;
        if (!sipStarted && callId.isNotEmpty) {
          nativeFailedCallIds.add(callId);
        }
        _addLog('SIP START $sipStatus ${jsonEncode(result)}');
      });
    } on PlatformException catch (error) {
      final message = [error.code, error.message]
          .where((part) => part != null && part.toString().isNotEmpty)
          .join(': ');
      if (!mounted) return;
      setState(() {
        sipStatus = 'error: ${_shortSipMessage(message)}';
        if (callId.isNotEmpty) {
          nativeFailedCallIds.add(callId);
        }
        _addLog('SIP ERROR ${error.code}: ${error.message}');
      });
      unawaited(_uploadNativeDiagnostics('sip_error'));
    }
  }

  Future<void> _stopNativeSip() async {
    if (nativeStartFuture != null) {
      await nativeStartFuture;
    }
    try {
      final result = await sipChannel.invokeMethod<Map<Object?, Object?>>('stop');
      if (!mounted) return;
      setState(() {
        sipStatus = result?['status']?.toString() ?? 'stopped';
        nativeCallId = null;
        nativeStartingCallId = null;
        nativeStartFuture = null;
        _addLog('SIP STOP $sipStatus');
      });
    } on PlatformException catch (error) {
      if (!mounted) return;
      setState(() {
        sipStatus = 'error: ${_shortSipMessage('${error.code}: ${error.message ?? ''}')}';
        _addLog('SIP STOP ERROR ${error.code}: ${error.message}');
      });
    }
  }

  Future<String> _loadNativeDiagnostics() async {
    try {
      final result = await sipChannel.invokeMethod<String>('diagnostics');
      return result ?? 'native diagnostics empty';
    } on PlatformException catch (error) {
      return 'native diagnostics error ${error.code}: ${error.message}';
    }
  }

  Future<void> _uploadNativeDiagnostics(String event) async {
    try {
      final diagnostics = await _loadNativeDiagnostics();
      await widget.apiService.uploadNativeLogs(
        userId: _selfId,
        event: event,
        text: diagnostics,
      );
    } catch (_) {}
  }

  void _syncCameraPreviewForCall(Map<String, Object?> call) {
    final status = call['status']?.toString() ?? '';
    final isVideo = call['mediaType']?.toString() == 'video';
    if (isVideo && (status == 'ringing' || status == 'answered')) {
      unawaited(_startCameraPreview());
    } else {
      unawaited(_stopCameraPreview());
    }
  }

  Future<void> _startCameraPreview() async {
    if (cameraController?.value.isInitialized == true) return;
    try {
      setState(() => cameraStatus = 'starting');
      final cameras = await availableCameras();
      if (cameras.isEmpty) {
        setState(() => cameraStatus = 'no_camera');
        _addLog('CAMERA ERROR no camera');
        return;
      }
      final frontCamera = cameras.firstWhere(
        (camera) => camera.lensDirection == CameraLensDirection.front,
        orElse: () => cameras.first,
      );
      final controller = CameraController(frontCamera, ResolutionPreset.medium, enableAudio: false);
      cameraController = controller;
      cameraInitFuture = controller.initialize();
      await cameraInitFuture;
      if (mounted) setState(() => cameraStatus = 'ready');
    } catch (error) {
      setState(() => cameraStatus = 'error');
      _addLog('CAMERA ERROR $error');
    }
  }

  Future<void> _stopCameraPreview() async {
    final controller = cameraController;
    cameraController = null;
    cameraInitFuture = null;
    if (mounted && cameraStatus != 'idle') {
      setState(() => cameraStatus = 'idle');
    }
    await controller?.dispose();
  }

  Future<void> refreshCalls() async {
    final data = await widget.apiService.getCalls(_selfId);
    calls = _dataList(data);
    final activeId = activeCall?['id']?.toString();
    Map<String, Object?>? nextActive;
    if (activeId != null && activeId.isNotEmpty) {
      for (final call in calls) {
        if (call['id']?.toString() == activeId) {
          nextActive = call;
          break;
        }
      }
    } else if (calls.isNotEmpty) {
      nextActive = calls.first;
    }
    if (nextActive != null) {
      await _applyCallUpdate(nextActive, 'HTTP REFRESH');
    }
  }

  Future<void> _pollActiveCall(String callId) async {
    if (callId.isEmpty) return;
    for (var attempt = 0; attempt < 20; attempt += 1) {
      await Future<void>.delayed(const Duration(seconds: 1));
      if (!mounted) return;
      final currentId = activeCall?['id']?.toString();
      final currentStatus = activeCall?['status']?.toString();
      if (currentId != callId || currentStatus == 'answered' || currentStatus == 'rejected' || currentStatus == 'ended') {
        return;
      }
      try {
        await refreshCalls();
      } catch (error) {
        if (mounted) setState(() => _addLog('CALL POLL ERROR $error'));
      }
    }
  }

  Future<void> _applyCallUpdate(Map<String, Object?> call, String source) async {
    await widget.storageService.saveCall(call);
    if (!mounted) return;
    setState(() {
      activeCall = call;
      _addLog('$source active=${call['id']} status=${call['status']}');
    });
    _syncCameraPreviewForCall(call);
    final status = call['status']?.toString();
    final mediaReady = call['mediaStatus'] == 'media_ready';
    if (mediaReady && (status == 'answered' || status == 'ringing')) {
      unawaited(_startNativeSipForCall(call));
    }
    if (status == 'rejected' || status == 'ended') {
      unawaited(_stopNativeSip());
      unawaited(_stopCameraPreview());
    }
  }

  Future<void> _runCallAction(Future<void> Function() action, {bool quiet = false}) async {
    setState(() => callLoading = true);
    try {
      await action();
    } catch (error) {
      if (!quiet) setState(() => _addLog('CALL ERROR $error'));
    } finally {
      if (mounted) setState(() => callLoading = false);
    }
  }

  // --- Logging ---

  Future<void> _pollGroupOnlineStatus() async {
    if (!widget.session.isGroup) return;
    try {
      final membersData = await widget.apiService.getGroupMembers(widget.session.id);
      final members = _dataList(membersData);
      final userIds = members
          .map((m) => m['userId']?.toString() ?? '')
          .where((id) => id.isNotEmpty)
          .toList();
      if (userIds.isEmpty) return;
      final statusData = await widget.apiService.getOnlineStatus(userIds);
      if (!mounted) return;
      var online = 0;
      for (final id in userIds) {
        if (statusData[id] == true) online++;
      }
      setState(() {
        _groupOnlineCount = online;
        _groupMemberCount = userIds.length;
      });
    } catch (_) {}
  }

  String _resolveSenderName(Message msg) {
    if (msg.mine) return '我';
    // Try to extract a display name from the sender ID
    // In a real app, you'd look up the member list from the session
    final senderId = msg.sender;
    if (senderId.isEmpty) return '未知用户';
    // Show a shortened version of the ID if no name mapping available
    if (senderId.length > 8) {
      return senderId.substring(0, 8);
    }
    return senderId;
  }

  void _addLog(String line) {
    final entry = '${DateTime.now().toIso8601String().substring(11, 23)} $line';
    logs.insert(0, entry);
    unawaited(widget.storageService.saveLog(entry));
  }

  // --- Debug ---

  void _showDebugSheet() {
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
              Text('SIP $sipStatus'),
              if (mediaConfig?['selfSipUri'] != null) Text('SIP URI ${mediaConfig!['selfSipUri']}'),
              const SizedBox(height: 10),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  OutlinedButton.icon(onPressed: _loadCallReadiness, icon: const Icon(Icons.fact_check), label: const Text('检测')),
                  OutlinedButton.icon(onPressed: refreshCalls, icon: const Icon(Icons.history), label: const Text('记录')),
                ],
              ),
              const Divider(height: 24),
              FutureBuilder<String>(
                future: _loadNativeDiagnostics(),
                builder: (context, snapshot) {
                  final text = snapshot.data ?? (snapshot.connectionState == ConnectionState.done ? 'native diagnostics empty' : 'loading...');
                  return ExpansionTile(
                    tilePadding: EdgeInsets.zero,
                    title: const Text('Native SIP / crash log'),
                    subtitle: Text(text.split('\n').first, maxLines: 1, overflow: TextOverflow.ellipsis),
                    children: [
                      SizedBox(
                        height: 180,
                        child: SingleChildScrollView(
                          child: SelectableText(text, style: const TextStyle(fontFamily: 'monospace', fontSize: 11)),
                        ),
                      ),
                    ],
                  );
                },
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

  Future<void> _saveConversationSettings(Map<String, bool> values) async {
    setState(() {
      conversationSettingsSaving = true;
      muted = values['muted'] ?? muted;
      pinned = values['pinned'] ?? pinned;
      screenshotNotice = values['screenshotNotice'] ?? screenshotNotice;
      recallNotice = values['recallNotice'] ?? recallNotice;
      readAfterBurn = values['readAfterBurn'] ?? readAfterBurn;
      strongReminder = values['strongReminder'] ?? strongReminder;
      displayMemberNicknames = values['displayMemberNicknames'] ?? displayMemberNicknames;
      savedToContacts = values['savedToContacts'] ?? savedToContacts;
    });
    try {
      final data = await widget.apiService.updateConversationSettings(widget.session.id, {
        'muted': muted,
        'pinned': pinned,
        'screenshotNotice': screenshotNotice,
        'recallNotice': recallNotice,
        'readAfterBurn': readAfterBurn,
        'strongReminder': strongReminder,
        'displayMemberNicknames': displayMemberNicknames,
        'savedToContacts': savedToContacts,
      });
      if (!mounted) return;
      setState(() {
        muted = data['muted'] == true;
        pinned = data['pinned'] == true;
        screenshotNotice = data['screenshotNotice'] != false;
        recallNotice = data['recallNotice'] != false;
        readAfterBurn = data['readAfterBurn'] == true;
        strongReminder = data['strongReminder'] == true;
        displayMemberNicknames = data['displayMemberNicknames'] != false;
        savedToContacts = data['savedToContacts'] == true;
        conversationSettingsSaving = false;
        _addLog('CONVERSATION SETTINGS saved ${widget.session.id}');
      });
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('会话设置已同步')));
    } catch (error) {
      if (!mounted) return;
      setState(() {
        conversationSettingsSaving = false;
        _addLog('CONVERSATION SETTINGS ERROR $error');
      });
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('会话设置保存失败: $error')));
    }
  }

  Future<void> _editGroupName() async {
    final controller = TextEditingController(text: widget.session.name);
    final result = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('修改群名称'),
        content: TextField(controller: controller, decoration: const InputDecoration(border: OutlineInputBorder())),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(context, controller.text.trim()), child: const Text('保存')),
        ],
      ),
    );
    if (result == null || result.isEmpty || result == widget.session.name) return;
    try {
      await widget.apiService.updateConversationSettings(widget.session.id, {'name': result});
      _addLog('GROUP RENAME $result');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('群名称已更新')));
      }
    } catch (error) {
      _addLog('GROUP RENAME ERROR $error');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('更新失败: $error')));
      }
    }
  }

  Future<void> _editGroupAnnouncement() async {
    final controller = TextEditingController();
    final result = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('群公告'),
        content: TextField(
          controller: controller,
          maxLines: 5,
          decoration: const InputDecoration(
            border: OutlineInputBorder(),
            hintText: '输入群公告内容',
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(context, controller.text.trim()), child: const Text('发布')),
        ],
      ),
    );
    if (result == null) return;
    try {
      await widget.apiService.updateGroupNotice(widget.session.id, result);
      _addLog('GROUP NOTICE updated');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('群公告已更新')));
      }
    } catch (error) {
      _addLog('GROUP NOTICE ERROR $error');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('更新失败: $error')));
      }
    }
  }

  Future<void> _transferGroupOwnership() async {
    final controller = TextEditingController();
    final result = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('转让群主'),
        content: TextField(
          controller: controller,
          decoration: const InputDecoration(
            border: OutlineInputBorder(),
            hintText: '输入新群主的用户ID',
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(context, controller.text.trim()), child: const Text('确认转让')),
        ],
      ),
    );
    if (result == null || result.isEmpty) return;
    try {
      await widget.apiService.transferGroupOwnership(widget.session.id, result);
      _addLog('GROUP OWNER TRANSFER to $result');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('群主已转让')));
      }
    } catch (error) {
      _addLog('GROUP OWNER TRANSFER ERROR $error');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('转让失败: $error')));
      }
    }
  }

  Future<void> _clearChatHistory() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('清空聊天记录'),
        content: const Text('确定要清空本会话的所有聊天记录吗？此操作不可撤销。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('清空'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await widget.apiService.clearConversationMessages(widget.session.id);
      _addLog('CLEAR HISTORY ${widget.session.id}');
      setState(() => messages.clear());
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('聊天记录已清空')));
      }
    } catch (error) {
      _addLog('CLEAR HISTORY ERROR $error');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('清空失败: $error')));
      }
    }
  }

  Future<void> _leaveGroup() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('退出群聊'),
        content: const Text('确定要退出此群聊吗？退出后需要重新被邀请才能加入。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('退出'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await widget.apiService.leaveGroup(widget.session.id);
      _addLog('LEAVE GROUP ${widget.session.id}');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已退出群聊')));
      }
      widget.onBack();
    } catch (error) {
      _addLog('LEAVE GROUP ERROR $error');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('退出失败: $error')));
      }
    }
  }

  void _showConversationSettings() {
    final draft = <String, bool>{
      'muted': muted,
      'pinned': pinned,
      'screenshotNotice': screenshotNotice,
      'recallNotice': recallNotice,
      'readAfterBurn': readAfterBurn,
      'strongReminder': strongReminder,
      'displayMemberNicknames': displayMemberNicknames,
      'savedToContacts': savedToContacts,
    };
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (context) => StatefulBuilder(
        builder: (context, setSheetState) {
          Widget switchTile(String key, String title, IconData icon) {
            return SwitchListTile(
              secondary: Icon(icon),
              title: Text(title),
              value: draft[key] ?? false,
              onChanged: (value) => setSheetState(() => draft[key] = value),
            );
          }

          return SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(widget.session.isGroup ? '群聊设置' : '单聊设置', style: Theme.of(context).textTheme.titleLarge),
                    subtitle: Text(widget.session.name),
                  ),
                  switchTile('muted', '消息免打扰', Icons.notifications_off_outlined),
                  switchTile('pinned', '置顶会话', Icons.push_pin_outlined),
                  switchTile('screenshotNotice', '截屏通知', Icons.screenshot_monitor_outlined),
                  switchTile('recallNotice', '撤回通知', Icons.undo_outlined),
                  switchTile('readAfterBurn', '阅后即焚', Icons.local_fire_department_outlined),
                  switchTile('strongReminder', '@/强提醒', Icons.alternate_email),
                  if (widget.session.isGroup) switchTile('displayMemberNicknames', '显示群成员昵称', Icons.badge_outlined),
                  if (widget.session.isGroup) switchTile('savedToContacts', '保存群到通讯录', Icons.group_add_outlined),
                  if (widget.session.isGroup) ...[
                    const Divider(),
                    ListTile(
                      leading: const Icon(Icons.edit_outlined),
                      title: const Text('编辑群名称'),
                      onTap: () {
                        Navigator.of(context).pop();
                        _editGroupName();
                      },
                    ),
                    ListTile(
                      leading: const Icon(Icons.campaign_outlined),
                      title: const Text('群公告'),
                      onTap: () {
                        Navigator.of(context).pop();
                        _editGroupAnnouncement();
                      },
                    ),
                    ListTile(
                      leading: const Icon(Icons.swap_horiz),
                      title: const Text('转让群主'),
                      onTap: () {
                        Navigator.of(context).pop();
                        _transferGroupOwnership();
                      },
                    ),
                    ListTile(
                      leading: const Icon(Icons.delete_sweep_outlined),
                      title: const Text('清空聊天记录'),
                      onTap: () {
                        Navigator.of(context).pop();
                        _clearChatHistory();
                      },
                    ),
                    ListTile(
                      leading: const Icon(Icons.exit_to_app, color: Colors.red),
                      title: const Text('退出群聊', style: TextStyle(color: Colors.red)),
                      onTap: () {
                        Navigator.of(context).pop();
                        _leaveGroup();
                      },
                    ),
                  ],
                  const SizedBox(height: 8),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: conversationSettingsSaving
                          ? null
                          : () {
                              Navigator.of(context).pop();
                              unawaited(_saveConversationSettings(draft));
                            },
                      icon: const Icon(Icons.cloud_done_outlined),
                      label: const Text('保存到服务器'),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  // --- Build ---

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final connected = widget.socketService.connected;
    final authenticated = widget.socketService.authenticated;
    final statusText = authenticated ? '在线' : (connected ? '连接中' : '离线');
    final statusColor = authenticated ? const Color(0xFF16A34A) : const Color(0xFF64748B);

    // If there's an active call visible, show the call page
    if (_activeCallVisible && activeCall != null) {
      return _buildCallPage();
    }

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: widget.onBack,
        ),
        titleSpacing: 8,
        title: Row(
          children: [
            CircleAvatar(
              radius: 18,
              backgroundColor: colorScheme.primaryContainer,
              child: Text(
                widget.session.name.isNotEmpty
                    ? widget.session.name.substring(0, 1).toUpperCase()
                    : 'U',
                style: TextStyle(
                  fontWeight: FontWeight.w700,
                  color: colorScheme.onPrimaryContainer,
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    widget.session.name,
                    style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    _typingPeer != null
                        ? '对方正在输入...'
                        : widget.session.isGroup && _groupMemberCount > 0
                            ? '$statusText · $_groupOnlineCount/$_groupMemberCount在线 · ${_callStatusText()}'
                            : '$statusText · ${_callStatusText()}',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: _typingPeer != null ? colorScheme.primary : statusColor,
                      fontSize: 11,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          if (multiSelectMode) ...[
            IconButton(
              tooltip: '转发选中',
              onPressed: selectedMessageIds.isEmpty ? null : () => _showForwardDialog(selectedMessageIds.toList()),
              icon: const Icon(Icons.forward),
            ),
            IconButton(
              tooltip: '退出多选',
              onPressed: () {
                setState(() {
                  multiSelectMode = false;
                  selectedMessageIds.clear();
                });
              },
              icon: const Icon(Icons.close),
            ),
          ] else
            IconButton(
              tooltip: '多选',
              onPressed: messages.any((message) => message.serverId != null && message.serverId!.isNotEmpty)
                  ? () => setState(() => multiSelectMode = true)
                  : null,
              icon: const Icon(Icons.checklist),
            ),
          IconButton(
            tooltip: '语音通话',
            onPressed: callLoading ? null : () => _startCall('audio'),
            icon: const Icon(Icons.call),
          ),
          IconButton(
            tooltip: '视频通话',
            onPressed: callLoading ? null : () => _startCall('video'),
            icon: const Icon(Icons.videocam),
          ),
          IconButton(
            tooltip: widget.session.isGroup ? '群聊设置' : '单聊设置',
            onPressed: conversationSettingsSaving ? null : _showConversationSettings,
            icon: const Icon(Icons.tune),
          ),
          IconButton(
            tooltip: '调试',
            onPressed: _showDebugSheet,
            icon: const Icon(Icons.bug_report_outlined),
          ),
        ],
      ),
      body: Column(
        children: [
          // Outgoing call bar
          if (_outgoingRinging)
            _buildOutgoingCallBar(),
          // Incoming call bar
          if (_incomingRinging)
            _buildIncomingCallBar(),
          // Message list
          Expanded(
            child: GestureDetector(
              onTap: () => FocusScope.of(context).unfocus(),
              child: ListView.builder(
                controller: scrollController,
                padding: const EdgeInsets.fromLTRB(14, 10, 14, 14),
                itemCount: messages.length,
                itemBuilder: (context, index) {
                  final msg = messages[index];
                  final isGroup = widget.session.isGroup;
                  final bubble = MessageBubble(
                    message: msg,
                    authToken: widget.apiService.token,
                    onLongPress: msg.direction != 'system'
                        ? () => multiSelectMode ? _toggleMessageSelection(msg) : _showMessageActions(msg)
                        : null,
                    showSender: isGroup && msg.direction != 'system',
                    senderName: isGroup ? _resolveSenderName(msg) : null,
                  );
                  if (!multiSelectMode) return bubble;
                  final selected = msg.serverId != null && selectedMessageIds.contains(msg.serverId);
                  return Row(
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: [
                      Checkbox(
                        value: selected,
                        onChanged: msg.serverId == null ? null : (_) => _toggleMessageSelection(msg),
                      ),
                      Expanded(child: bubble),
                    ],
                  );
                },
              ),
            ),
          ),
          // Input bar
          if (uploadProgress != null)
            LinearProgressIndicator(
              value: uploadProgress!.clamp(0, 1),
              minHeight: 3,
            ),
          _buildInputBar(colorScheme),
        ],
      ),
    );
  }

  Widget _buildInputBar(ColorScheme colorScheme) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colorScheme.surface,
        border: Border(top: BorderSide(color: colorScheme.outlineVariant)),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 9, 12, 12),
        child: Row(
          children: [
            IconButton.filledTonal(
              onPressed: widget.socketService.sendPing,
              tooltip: '心跳',
              icon: const Icon(Icons.sync),
            ),
            IconButton.filledTonal(
              onPressed: widget.socketService.authenticated ? _pickAndSendFile : null,
              tooltip: '发送文件',
              icon: const Icon(Icons.attach_file),
            ),
            GestureDetector(
              onLongPressStart: widget.socketService.authenticated ? (_) => _startRecording() : null,
              onLongPressEnd: widget.socketService.authenticated ? (_) => _stopRecordingAndSend() : null,
              child: IconButton.filledTonal(
                onPressed: null,
                icon: Icon(_isRecording ? Icons.fiber_manual_record : Icons.mic, color: _isRecording ? Colors.red : null),
              ),
            ),
            Expanded(
              child: TextField(
                controller: messageController,
                minLines: 1,
                maxLines: 4,
                textInputAction: TextInputAction.send,
                onSubmitted: (_) => _sendText(),
                onChanged: (_) {
                  final peerId = widget.session.peerId;
                  if (peerId == null || peerId.isEmpty) return;
                  _typingTimer?.cancel();
                  _typingTimer = Timer(const Duration(milliseconds: 400), () {
                    widget.socketService.sendTyping(
                      to: peerId,
                      conversationId: widget.session.id,
                      isTyping: messageController.text.isNotEmpty,
                    );
                  });
                },
                decoration: InputDecoration(
                  hintText: widget.socketService.authenticated ? '输入消息' : '先连接认证',
                  filled: true,
                  fillColor: const Color(0xFFF8FAFC),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none),
                  contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                ),
              ),
            ),
            const SizedBox(width: 8),
            IconButton.filled(
              onPressed: widget.socketService.authenticated ? _sendText : null,
              tooltip: '发送',
              icon: const Icon(Icons.send),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildOutgoingCallBar() {
    final mediaType = activeCall?['mediaType']?.toString() ?? 'audio';
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
              FilledButton.tonalIcon(
                onPressed: _hangupCall,
                icon: const Icon(Icons.call_end),
                label: const Text('取消'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildIncomingCallBar() {
    final mediaType = activeCall?['mediaType']?.toString() ?? 'audio';
    final callerId = _activeCallerId;
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
              OutlinedButton(onPressed: _rejectCall, child: const Text('拒绝')),
              const SizedBox(width: 8),
              FilledButton(onPressed: _answerCall, child: const Text('接听')),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCallPage() {
    final isVideo = activeCall!['mediaType']?.toString() == 'video';
    final status = activeCall!['status']?.toString() ?? '';
    final isRinging = status == 'ringing';
    final isIncoming = _incomingRinging;
    final peerId = _peerIdForCall(activeCall!);
    final normalizedSipStatus = sipStatus.toLowerCase();
    final remoteVideoEnabled = Platform.isAndroid &&
        normalizedSipStatus != 'idle' &&
        normalizedSipStatus != 'stopped' &&
        normalizedSipStatus != 'permission_required' &&
        normalizedSipStatus != 'unknown' &&
        !normalizedSipStatus.startsWith('error');

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
        actions: [
          IconButton(onPressed: _showDebugSheet, icon: const Icon(Icons.bug_report_outlined)),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(18, 12, 18, 0),
                child: isVideo
                    ? _buildVideoCallStage(remoteVideoEnabled)
                    : _buildAudioCallStage(peerId),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 12, 24, 6),
              child: Column(
                children: [
                  Text(
                    stateText,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      color: Colors.white,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
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
                  if (isIncoming) ...[
                    Expanded(
                      child: FilledButton.icon(
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFFDC2626),
                          minimumSize: const Size.fromHeight(54),
                        ),
                        onPressed: callLoading ? null : _rejectCall,
                        icon: const Icon(Icons.call_end),
                        label: const Text('拒绝'),
                      ),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: FilledButton.icon(
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFF16A34A),
                          minimumSize: const Size.fromHeight(54),
                        ),
                        onPressed: callLoading ? null : _answerCall,
                        icon: const Icon(Icons.call),
                        label: const Text('接听'),
                      ),
                    ),
                  ] else ...[
                    Expanded(
                      child: FilledButton.icon(
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFFDC2626),
                          minimumSize: const Size.fromHeight(54),
                        ),
                        onPressed: callLoading ? null : _hangupCall,
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

  Widget _buildVideoCallStage(bool remoteEnabled) {
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
              const Center(
                child: Text('等待接听后显示远端视频', style: TextStyle(color: Color(0xFFCBD5E1), fontSize: 16)),
              ),
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
                      ? _LocalCameraPreview(controller: controller, future: future)
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
                decoration: BoxDecoration(
                  color: Colors.black.withAlpha(115),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  child: Text(
                    '主画面：远端视频；右上角：本机预览',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.white),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAudioCallStage(String peerId) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircleAvatar(
            radius: 54,
            backgroundColor: const Color(0xFF1D4ED8),
            child: Text(
              peerId.isEmpty ? 'U' : peerId.substring(0, 1).toUpperCase(),
              style: const TextStyle(color: Colors.white, fontSize: 40, fontWeight: FontWeight.w800),
            ),
          ),
          const SizedBox(height: 24),
          const Icon(Icons.graphic_eq, color: Color(0xFF93C5FD), size: 56),
        ],
      ),
    );
  }
}

class _LocalCameraPreview extends StatelessWidget {
  const _LocalCameraPreview({required this.controller, required this.future});

  final CameraController controller;
  final Future<void> future;

  @override
  Widget build(BuildContext context) {
    if (controller.value.isInitialized) {
      return FittedBox(
        fit: BoxFit.cover,
        child: SizedBox(
          width: controller.value.previewSize?.height ?? 720,
          height: controller.value.previewSize?.width ?? 1280,
          child: CameraPreview(controller),
        ),
      );
    }
    return FutureBuilder<void>(
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
    );
  }
}
