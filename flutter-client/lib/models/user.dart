class AppUser {
  AppUser({
    required this.id,
    required this.name,
    this.phone,
    this.avatar,
    this.online = false,
    this.lastSeen,
    this.gender,
    this.region,
    this.signature,
    this.source,
  });

  final String id;
  final String name;
  final String? phone;
  final String? avatar;
  final bool online;
  final int? lastSeen; // timestamp in milliseconds
  final String? gender;
  final String? region;
  final String? signature;
  final String? source;

  factory AppUser.fromJson(Map<String, Object?> json) {
    return AppUser(
      id: json['id']?.toString() ?? '',
      name: json['name']?.toString() ?? json['nickname']?.toString() ?? '',
      phone: json['phone']?.toString(),
      avatar: json['avatar']?.toString(),
      online: json['online'] == true,
      lastSeen: json['lastSeen'] is int
          ? json['lastSeen'] as int
          : int.tryParse(json['lastSeen']?.toString() ?? ''),
      gender: json['gender']?.toString(),
      region: json['region']?.toString(),
      signature: json['signature']?.toString(),
      source: json['source']?.toString(),
    );
  }

  Map<String, Object?> toJson() {
    return {
      'id': id,
      'name': name,
      'phone': phone,
      'avatar': avatar,
      'online': online,
      'lastSeen': lastSeen,
      'gender': gender,
      'region': region,
      'signature': signature,
      'source': source,
    };
  }
}
