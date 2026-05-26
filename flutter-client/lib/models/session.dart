import 'dart:convert';

class Session {
  Session({
    required this.id,
    required this.name,
    this.type = 'single',
    this.avatar,
    this.lastMessage,
    this.lastMessageTime,
    this.unreadCount = 0,
    this.peerId,
    this.online = false,
    this.muted = false,
    this.pinned = false,
    this.screenshotNotice = true,
    this.recallNotice = true,
    this.readAfterBurn = false,
    this.strongReminder = false,
    this.displayMemberNicknames = true,
    this.savedToContacts = false,
  });

  final String id;
  final String name;
  final String type; // 'single' or 'group'
  final String? avatar;
  final String? lastMessage;
  final DateTime? lastMessageTime;
  final int unreadCount;
  final String? peerId;
  final bool online;
  final bool muted;
  final bool pinned;
  final bool screenshotNotice;
  final bool recallNotice;
  final bool readAfterBurn;
  final bool strongReminder;
  final bool displayMemberNicknames;
  final bool savedToContacts;

  bool get isGroup => type == 'group';

  factory Session.fromJson(Map<String, Object?> json) {
    final members = json['members'];
    final String type = json['type']?.toString() ?? 'single';
    final bool isGroup = type == 'group';
    String? peerId = json['targetId']?.toString();
    String name = _firstText(json, [
      'name',
      'targetName',
      'displayName',
      'title',
      'conversationName',
    ]);

    if (name.isEmpty && members is List) {
      name = members
          .map((m) => m is Map ? (m['name']?.toString() ?? m['userId']?.toString() ?? '') : '')
          .where((value) => value.isNotEmpty)
          .join(', ');
    }

    if (!isGroup && (peerId == null || peerId.isEmpty) && members is List) {
      for (final m in members) {
        if (m is Map) {
          final uid = m['userId']?.toString();
          if (uid != null && uid.isNotEmpty) {
            peerId = uid;
            break;
          }
        }
      }
    }

    if (name.isEmpty) {
      name = peerId?.isNotEmpty == true ? peerId! : (json['id']?.toString() ?? '会话');
    }

    final lastType = json['lastType']?.toString() ?? json['lastMessageType']?.toString();
    final lastContent = _firstText(json, ['lastMessage', 'lastMessageContent', 'lastContent']);

    return Session(
      id: json['id']?.toString() ?? '',
      name: name,
      type: type,
      avatar: json['avatar']?.toString(),
      lastMessage: _readableMessage(lastType, lastContent),
      lastMessageTime: _parseTime(json['lastMessageTime'] ?? json['lastTime'] ?? json['updatedAt']),
      unreadCount: _parseInt(json['unreadCount']),
      peerId: peerId,
      online: json['online'] == true,
      muted: json['muted'] == true,
      pinned: json['pinned'] == true,
      screenshotNotice: json['screenshotNotice'] != false,
      recallNotice: json['recallNotice'] != false,
      readAfterBurn: json['readAfterBurn'] == true,
      strongReminder: json['strongReminder'] == true,
      displayMemberNicknames: json['displayMemberNicknames'] != false,
      savedToContacts: json['savedToContacts'] == true,
    );
  }

  static String _firstText(Map<String, Object?> json, List<String> keys) {
    for (final key in keys) {
      final value = json[key];
      if (value != null && value.toString().isNotEmpty) {
        return value.toString();
      }
    }
    return '';
  }

  static String? _readableMessage(String? type, String content) {
    if (content.isEmpty) return null;
    if (type == 'location' || type == 'card') {
      try {
        final decoded = jsonDecode(content);
        if (decoded is Map) {
          final name = decoded['name']?.toString();
          final address = decoded['address']?.toString();
          if (type == 'location') {
            return '位置：${name?.isNotEmpty == true ? name : (address ?? '位置消息')}';
          }
          return '名片：${name?.isNotEmpty == true ? name : '个人名片'}';
        }
      } catch (_) {
        // Fall through to plain content.
      }
    }
    if (content.startsWith('{') && content.endsWith('}')) {
      return type == 'file' ? '文件' : '消息';
    }
    return content;
  }

  static DateTime? _parseTime(Object? value) {
    if (value == null) return null;
    if (value is DateTime) return value;
    return DateTime.tryParse(value.toString());
  }

  static int _parseInt(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }
}
