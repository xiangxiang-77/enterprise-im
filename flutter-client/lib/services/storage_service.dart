import 'package:path/path.dart' as path;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';

import '../models/message.dart';

class StorageService {
  Database? _db;
  SharedPreferences? _prefs;

  Database? get db => _db;
  SharedPreferences? get prefs => _prefs;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    final dbPath = path.join(await getDatabasesPath(), 'enterprise_im_mobile.db');
    _db = await openDatabase(
      dbPath,
      version: 4,
      onCreate: (database, version) async {
        await _createTables(database);
      },
      onUpgrade: (database, oldVersion, newVersion) async {
        if (oldVersion < 2) {
          await database.execute(
            'ALTER TABLE local_messages ADD COLUMN server_id TEXT',
          );
        }
        if (oldVersion < 3) {
          await database.execute('ALTER TABLE local_messages ADD COLUMN type TEXT DEFAULT "text"');
          await database.execute('ALTER TABLE local_messages ADD COLUMN file_url TEXT');
          await database.execute('ALTER TABLE local_messages ADD COLUMN file_name TEXT');
          await database.execute('ALTER TABLE local_messages ADD COLUMN file_size INTEGER');
          await database.execute('ALTER TABLE local_messages ADD COLUMN thumbnail_url TEXT');
        }
        if (oldVersion < 4) {
          await _createUploadQueueTable(database);
        }
      },
    );
  }

  Future<void> _createTables(Database database) async {
    await database.execute(
      'CREATE TABLE local_logs(id INTEGER PRIMARY KEY AUTOINCREMENT, line TEXT NOT NULL, created_at TEXT NOT NULL)',
    );
    await database.execute(
      'CREATE TABLE local_messages(id INTEGER PRIMARY KEY AUTOINCREMENT, conversation_id TEXT, server_id TEXT, sender TEXT, content TEXT NOT NULL, direction TEXT NOT NULL, created_at TEXT NOT NULL, type TEXT DEFAULT "text", file_url TEXT, file_name TEXT, file_size INTEGER, thumbnail_url TEXT)',
    );
    await database.execute(
      'CREATE TABLE local_calls(id INTEGER PRIMARY KEY AUTOINCREMENT, call_id TEXT NOT NULL, media_type TEXT, status TEXT, created_at TEXT NOT NULL)',
    );
    await _createUploadQueueTable(database);
  }

  Future<void> _createUploadQueueTable(Database database) async {
    await database.execute(
      'CREATE TABLE IF NOT EXISTS upload_queue(id INTEGER PRIMARY KEY AUTOINCREMENT, conversation_id TEXT NOT NULL, peer_id TEXT, file_path TEXT NOT NULL, original_name TEXT NOT NULL, retry_count INTEGER DEFAULT 0, status TEXT NOT NULL, last_error TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)',
    );
  }

  // SharedPreferences helpers
  String? getToken() => _prefs?.getString('auth_token');
  String? getUserId() => _prefs?.getString('user_id');
  String? getUserName() => _prefs?.getString('user_name');
  String? getHost() => _prefs?.getString('host');
  String? getHttpPort() => _prefs?.getString('http_port');
  String? getTcpPort() => _prefs?.getString('tcp_port');

  Future<void> saveAuth(String token, String userId, String userName) async {
    await _prefs?.setString('auth_token', token);
    await _prefs?.setString('user_id', userId);
    await _prefs?.setString('user_name', userName);
  }

  Future<void> saveServerConfig(String host, String httpPort, String tcpPort) async {
    await _prefs?.setString('host', host);
    await _prefs?.setString('http_port', httpPort);
    await _prefs?.setString('tcp_port', tcpPort);
  }

  Future<void> clearAuth() async {
    await _prefs?.remove('auth_token');
    await _prefs?.remove('user_id');
    await _prefs?.remove('user_name');
  }

  bool get isLoggedIn => (_prefs?.getString('auth_token') ?? '').isNotEmpty;

  // Font size preference
  String getFontSize() => _prefs?.getString('font_size') ?? 'standard';
  Future<void> saveFontSize(String size) async {
    await _prefs?.setString('font_size', size);
  }

  // Dark mode preference
  bool getDarkMode() => _prefs?.getBool('dark_mode') ?? false;
  Future<void> saveDarkMode(bool enabled) async {
    await _prefs?.setBool('dark_mode', enabled);
  }

  // Notification settings
  bool getNotifMessageEnabled() => _prefs?.getBool('notif_message') ?? true;
  Future<void> saveNotifMessageEnabled(bool v) async => _prefs?.setBool('notif_message', v);

  bool getNotifAtEnabled() => _prefs?.getBool('notif_at') ?? true;
  Future<void> saveNotifAtEnabled(bool v) async => _prefs?.setBool('notif_at', v);

  bool getNotifRecallEnabled() => _prefs?.getBool('notif_recall') ?? true;
  Future<void> saveNotifRecallEnabled(bool v) async => _prefs?.setBool('notif_recall', v);

  bool getNotifScreenshotEnabled() => _prefs?.getBool('notif_screenshot') ?? false;
  Future<void> saveNotifScreenshotEnabled(bool v) async => _prefs?.setBool('notif_screenshot', v);

  // SQLite log helpers
  Future<void> saveLog(String line) async {
    final database = _db;
    if (database == null || !database.isOpen) return;
    await database.insert('local_logs', {
      'line': line,
      'created_at': DateTime.now().toIso8601String(),
    });
  }

  // SQLite message helpers
  Future<void> saveMessage({
    required String? conversationId,
    required String sender,
    required String content,
    required String direction,
    String? serverId,
    String type = 'text',
    String? fileUrl,
    String? fileName,
    int? fileSize,
    String? thumbnailUrl,
  }) async {
    final database = _db;
    if (database == null || !database.isOpen || content.isEmpty) return;
    await database.insert('local_messages', {
      'conversation_id': conversationId,
      'server_id': serverId,
      'sender': sender,
      'content': content,
      'direction': direction,
      'created_at': DateTime.now().toIso8601String(),
      'type': type,
      'file_url': fileUrl,
      'file_name': fileName,
      'file_size': fileSize,
      'thumbnail_url': thumbnailUrl,
    });
  }

  Future<List<Message>> loadMessages(String conversationId, {int limit = 50}) async {
    final database = _db;
    if (database == null || !database.isOpen) return [];
    final rows = await database.query(
      'local_messages',
      where: 'conversation_id = ?',
      whereArgs: [conversationId],
      orderBy: 'id DESC',
      limit: limit,
    );
    return rows.reversed.map((row) => Message.fromLocalRow(row)).toList();
  }

  Future<int> enqueueUpload({
    required String conversationId,
    required String? peerId,
    required String filePath,
    required String originalName,
    String? error,
  }) async {
    final database = _db;
    if (database == null || !database.isOpen) return -1;
    final now = DateTime.now().toIso8601String();
    return database.insert('upload_queue', {
      'conversation_id': conversationId,
      'peer_id': peerId,
      'file_path': filePath,
      'original_name': originalName,
      'retry_count': 0,
      'status': 'pending',
      'last_error': error,
      'created_at': now,
      'updated_at': now,
    });
  }

  Future<List<Map<String, Object?>>> loadPendingUploads(String conversationId) async {
    final database = _db;
    if (database == null || !database.isOpen) return <Map<String, Object?>>[];
    return database.query(
      'upload_queue',
      where: 'conversation_id = ? AND status = ?',
      whereArgs: [conversationId, 'pending'],
      orderBy: 'id ASC',
      limit: 20,
    );
  }

  Future<void> markUploadQueued(int id, {String? error, int? retryCount}) async {
    final database = _db;
    if (database == null || !database.isOpen) return;
    await database.update(
      'upload_queue',
      {
        if (retryCount != null) 'retry_count': retryCount,
        if (error != null) 'last_error': error,
        'updated_at': DateTime.now().toIso8601String(),
      },
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<void> deleteUploadQueueItem(int id) async {
    final database = _db;
    if (database == null || !database.isOpen) return;
    await database.delete('upload_queue', where: 'id = ?', whereArgs: [id]);
  }

  // SQLite call helpers
  Future<void> saveCall(Map<String, Object?> call) async {
    final database = _db;
    final callId = call['id']?.toString();
    if (database == null || !database.isOpen || callId == null || callId.isEmpty) return;
    await database.insert('local_calls', {
      'call_id': callId,
      'media_type': call['mediaType']?.toString(),
      'status': call['status']?.toString(),
      'created_at': DateTime.now().toIso8601String(),
    });
  }

  void close() {
    _db?.close();
  }
}
