import 'package:flutter/material.dart';

import '../models/session.dart';

class SessionItem extends StatelessWidget {
  const SessionItem({
    super.key,
    required this.session,
    required this.onTap,
  });

  final Session session;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return ListTile(
      onTap: onTap,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      leading: Stack(
        children: [
          CircleAvatar(
            radius: 26,
            backgroundColor: session.isGroup
                ? colorScheme.tertiaryContainer
                : colorScheme.primaryContainer,
            child: Text(
              session.name.isNotEmpty ? session.name.characters.first.toUpperCase() : '会',
              style: TextStyle(
                fontWeight: FontWeight.w700,
                fontSize: 18,
                color: session.isGroup
                    ? colorScheme.onTertiaryContainer
                    : colorScheme.onPrimaryContainer,
              ),
            ),
          ),
          if (session.isGroup)
            Positioned(
              right: 0,
              bottom: 0,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: colorScheme.tertiary,
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.white, width: 2),
                ),
                child: const Padding(
                  padding: EdgeInsets.all(2),
                  child: Icon(Icons.group, size: 10, color: Colors.white),
                ),
              ),
            ),
          if (!session.isGroup && session.online)
            Positioned(
              right: 0,
              bottom: 0,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: const Color(0xFF16A34A),
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.white, width: 2),
                ),
                child: const SizedBox(width: 12, height: 12),
              ),
            ),
        ],
      ),
      title: Row(
        children: [
          Expanded(
            child: Text(
              session.name,
              style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (session.lastMessageTime != null)
            Text(
              _formatTime(session.lastMessageTime!),
              style: TextStyle(
                fontSize: 12,
                color: session.unreadCount > 0
                    ? colorScheme.primary
                    : colorScheme.onSurfaceVariant,
              ),
            ),
        ],
      ),
      subtitle: Padding(
        padding: const EdgeInsets.only(top: 4),
        child: Row(
          children: [
            Expanded(
              child: Text(
                session.lastMessage ?? '',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 13,
                  color: colorScheme.onSurfaceVariant,
                ),
              ),
            ),
            if (session.unreadCount > 0)
              Container(
                margin: const EdgeInsets.only(left: 8),
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                decoration: BoxDecoration(
                  color: colorScheme.error,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  session.unreadCount > 99 ? '99+' : '${session.unreadCount}',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  String _formatTime(DateTime time) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final messageDay = DateTime(time.year, time.month, time.day);

    if (messageDay == today) {
      return '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
    }
    if (messageDay == today.subtract(const Duration(days: 1))) {
      return '昨天';
    }
    if (now.difference(messageDay).inDays < 7) {
      const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
      return weekdays[time.weekday - 1];
    }
    return '${time.month}/${time.day}';
  }
}
