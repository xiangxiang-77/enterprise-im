class Message {
  Message({
    required this.sender,
    required this.content,
    required this.direction,
    required this.createdAt,
    this.serverId,
    this.conversationId,
    this.reaction,
    this.recalled = false,
    this.type = 'text',
    this.fileUrl,
    this.fileName,
    this.fileSize,
    this.thumbnailUrl,
    this.expireAfterReadSeconds,
  });

  final String sender;
  String content;
  final String direction; // 'in', 'out', 'system'
  final DateTime createdAt;
  final String? serverId;
  final String? conversationId;
  String? reaction;
  bool recalled;

  // New fields for rich message types
  final String type; // 'text', 'image', 'file', 'voice', 'video', 'card', 'system'
  final String? fileUrl;
  final String? fileName;
  final int? fileSize;
  final String? thumbnailUrl;

  // Burn-after-read: remaining seconds before the message expires
  final int? expireAfterReadSeconds;

  bool get mine => direction == 'out';

  factory Message.fromLocalRow(Map<String, Object?> row) {
    return Message(
      sender: row['sender']?.toString() ?? '',
      content: row['content']?.toString() ?? '',
      direction: row['direction']?.toString() ?? 'in',
      createdAt: DateTime.tryParse(row['created_at']?.toString() ?? '') ?? DateTime.now(),
      serverId: row['server_id']?.toString(),
      conversationId: row['conversation_id']?.toString(),
      type: row['type']?.toString() ?? 'text',
      fileUrl: row['file_url']?.toString(),
      fileName: row['file_name']?.toString(),
      fileSize: _parseInt(row['file_size']),
      thumbnailUrl: row['thumbnail_url']?.toString(),
      expireAfterReadSeconds: _parseInt(row['expire_after_read_seconds']),
    );
  }

  factory Message.fromJson(Map<String, Object?> json) {
    return Message(
      sender: json['senderId']?.toString() ?? json['sender']?.toString() ?? '',
      content: json['content']?.toString() ?? '',
      direction: 'in',
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.now(),
      serverId: json['id']?.toString(),
      conversationId: json['conversationId']?.toString(),
      type: json['type']?.toString() ?? 'text',
      fileUrl: json['fileUrl']?.toString(),
      fileName: json['fileName']?.toString(),
      fileSize: _parseInt(json['fileSize']),
      thumbnailUrl: json['videoThumbnail']?.toString(),
      expireAfterReadSeconds: _parseInt(json['expireAfterReadSeconds'] ?? json['expireAfterRead']),
    );
  }

  factory Message.system(String content) {
    return Message(
      sender: '系统',
      content: content,
      direction: 'system',
      createdAt: DateTime.now(),
      type: 'system',
    );
  }

  Map<String, Object?> toLocalRow() {
    return {
      'conversation_id': conversationId,
      'server_id': serverId,
      'sender': sender,
      'content': content,
      'direction': direction,
      'created_at': createdAt.toIso8601String(),
      'type': type,
      'file_url': fileUrl,
      'file_name': fileName,
      'file_size': fileSize,
      'thumbnail_url': thumbnailUrl,
      'expire_after_read_seconds': expireAfterReadSeconds,
    };
  }

  static int? _parseInt(Object? value) {
    if (value == null) return null;
    if (value is int) return value;
    return int.tryParse(value.toString());
  }
}
