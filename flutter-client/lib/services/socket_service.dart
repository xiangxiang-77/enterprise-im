import 'dart:async';
import 'dart:convert';
import 'dart:io';

typedef SocketFrameCallback = void Function(Map<String, Object?> frame);
typedef SocketStateCallback = void Function(bool connected, bool authenticated);
typedef SocketLogCallback = void Function(String log);

class SocketService {
  Socket? _socket;
  StreamSubscription<List<int>>? _subscription;
  String _pending = '';
  bool _connected = false;
  bool _authenticated = false;
  bool _manualDisconnect = false;
  Timer? _reconnectTimer;
  int _reconnectAttempts = 0;

  String host = '';
  int port = 0;
  String userId = '';
  String token = '';

  SocketFrameCallback? onFrame;
  SocketStateCallback? onStateChanged;
  SocketLogCallback? onLog;

  bool get connected => _connected;
  bool get authenticated => _authenticated;

  Future<void> connect(String host, int port, String userId, String token) async {
    this.host = host;
    this.port = port;
    this.userId = userId;
    this.token = token;

    _manualDisconnect = false;
    if (_connected) return;
    await _connectInternal();
  }

  Future<void> _connectInternal() async {
    _reconnectTimer?.cancel();

    try {
      final nextSocket = await Socket.connect(host, port, timeout: const Duration(seconds: 5));
      _subscription = nextSocket.listen(
        _onData,
        onError: (error) {
          _log('TCP ERROR $error');
        },
        onDone: () {
          _connected = false;
          _authenticated = false;
          onStateChanged?.call(false, false);
          _log('TCP DISCONNECTED');
          _scheduleReconnect();
        },
      );
      _socket = nextSocket;
      _connected = true;
      _authenticated = false;
      _reconnectAttempts = 0;
      onStateChanged?.call(true, false);
      _log('TCP CONNECTED $host:$port');
      sendAuth();
    } catch (error) {
      _log('TCP ERROR $error');
      _connected = false;
      _authenticated = false;
      onStateChanged?.call(false, false);
      _scheduleReconnect();
    }
  }

  void disconnect() {
    _manualDisconnect = true;
    _reconnectTimer?.cancel();
    _subscription?.cancel();
    _socket?.destroy();
    _socket = null;
    _connected = false;
    _authenticated = false;
    _pending = '';
    onStateChanged?.call(false, false);
    _log('TCP DISCONNECTED');
  }

  void _scheduleReconnect() {
    if (_manualDisconnect || host.isEmpty || port == 0 || userId.isEmpty || token.isEmpty) return;
    if (_reconnectTimer?.isActive ?? false) return;
    _reconnectAttempts = (_reconnectAttempts + 1).clamp(1, 6);
    final delay = Duration(seconds: 1 << (_reconnectAttempts - 1));
    _log('TCP RECONNECT in ${delay.inSeconds}s');
    _reconnectTimer = Timer(delay, () {
      if (!_manualDisconnect && !_connected) {
        unawaited(_connectInternal());
      }
    });
  }

  void sendAuth() {
    sendFrame('AUTH', payload: {'token': token});
  }

  void sendPing() {
    sendFrame('PING');
  }

  void sendText({
    required String to,
    required String conversationId,
    required String content,
  }) {
    sendFrame(
      'TEXT',
      to: to,
      conversationId: conversationId,
      payload: {'content': content},
    );
  }

  void sendTyping({
    required String to,
    required String conversationId,
    required bool isTyping,
  }) {
    sendFrame(
      'TYPING',
      to: to,
      conversationId: conversationId,
      payload: {'isTyping': isTyping},
    );
  }

  void sendFrame(String type, {String? to, String? conversationId, Map<String, Object?> payload = const {}}) {
    final currentSocket = _socket;
    if (currentSocket == null || !_connected) {
      _log('TCP ERROR socket is not connected');
      return;
    }

    final frame = <String, Object?>{
      'version': '1',
      'type': type,
      'requestId': '${DateTime.now().microsecondsSinceEpoch}',
      'from': userId,
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
    _log('TCP OUT $line');
  }

  void _onData(List<int> bytes) {
    _pending += utf8.decode(bytes);
    var newline = _pending.indexOf('\n');
    while (newline >= 0) {
      final line = _pending.substring(0, newline).trim();
      _pending = _pending.substring(newline + 1);
      if (line.isNotEmpty) {
        _handleLine(line);
      }
      newline = _pending.indexOf('\n');
    }
  }

  void _handleLine(String line) {
    _log('TCP IN $line');
    try {
      final decoded = jsonDecode(line);
      if (decoded is Map) {
        final frame = Map<String, Object?>.from(decoded);
        final type = frame['type']?.toString();

        if (type == 'AUTH_OK') {
          _authenticated = true;
          onStateChanged?.call(_connected, true);
          onFrame?.call(frame);
          return;
        }
        if (type == 'AUTH_FAILED') {
          _authenticated = false;
          onStateChanged?.call(_connected, false);
          onFrame?.call(frame);
          return;
        }

        onFrame?.call(frame);
      }
    } catch (_) {
      // Keep raw logging for malformed frames.
    }
  }

  void _log(String line) {
    final entry = '${DateTime.now().toIso8601String().substring(11, 23)} $line';
    onLog?.call(entry);
  }
}
