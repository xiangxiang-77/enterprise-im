import 'dart:async';

import 'package:flutter/material.dart';

import '../models/session.dart';
import '../models/user.dart';
import '../services/api_service.dart';
import '../services/socket_service.dart';
import '../widgets/session_item.dart';

class SessionListScreen extends StatefulWidget {
  const SessionListScreen({
    super.key,
    required this.apiService,
    required this.socketService,
    required this.currentUser,
    required this.onOpenChat,
    required this.onOpenContacts,
    required this.onLogout,
    required this.onOpenSettings,
    required this.onOpenSearch,
    this.onOpenFileManager,
    this.onOpenOrganization,
  });

  final ApiService apiService;
  final SocketService socketService;
  final AppUser currentUser;
  final void Function(Session session) onOpenChat;
  final VoidCallback onOpenContacts;
  final VoidCallback onLogout;
  final VoidCallback onOpenSettings;
  final VoidCallback onOpenSearch;
  final VoidCallback? onOpenFileManager;
  final VoidCallback? onOpenOrganization;

  @override
  State<SessionListScreen> createState() => _SessionListScreenState();
}

class _SessionListScreenState extends State<SessionListScreen> {
  List<Session> _sessions = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    unawaited(_loadSessions());
  }

  Future<void> _loadSessions() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await widget.apiService.getConversations();
      final items = _dataList(data);
      setState(() {
        _sessions = items.map((item) => Session.fromJson(item)).toList();
        _loading = false;
      });
    } catch (error) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = '加载会话失败: $error';
        });
      }
    }
  }

  List<Map<String, Object?>> _dataList(Map<String, Object?> data) {
    final items = data['items'] ?? data['conversations'] ?? data['data'];
    if (items is List) {
      return items.whereType<Map>().map((item) => Map<String, Object?>.from(item)).toList();
    }
    return [];
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final connected = widget.socketService.connected;
    final authenticated = widget.socketService.authenticated;
    final statusText = authenticated ? '在线' : (connected ? '连接中' : '离线');
    final statusColor = authenticated ? const Color(0xFF16A34A) : const Color(0xFF64748B);

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 16,
        title: Row(
          children: [
            CircleAvatar(
              radius: 17,
              backgroundColor: colorScheme.primaryContainer,
              child: Text(
                widget.currentUser.name.isNotEmpty
                    ? widget.currentUser.name.substring(0, 1).toUpperCase()
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
                  const Text(
                    '企业 IM',
                    style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
                  ),
                  Text(
                    '$statusText · ${widget.currentUser.name}',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(color: statusColor),
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: '搜索',
            onPressed: widget.onOpenSearch,
            icon: const Icon(Icons.search),
          ),
          IconButton(
            tooltip: '通讯录',
            onPressed: widget.onOpenContacts,
            icon: const Icon(Icons.contacts_outlined),
          ),
          IconButton(
            tooltip: '设置',
            onPressed: widget.onOpenSettings,
            icon: const Icon(Icons.settings_outlined),
          ),
          PopupMenuButton<String>(
            onSelected: (value) {
              if (value == 'logout') {
                widget.onLogout();
              } else if (value == 'refresh') {
                unawaited(_loadSessions());
              } else if (value == 'files') {
                widget.onOpenFileManager?.call();
              } else if (value == 'org') {
                widget.onOpenOrganization?.call();
              }
            },
            itemBuilder: (context) => [
              const PopupMenuItem(value: 'refresh', child: Text('刷新')),
              if (widget.onOpenFileManager != null)
                const PopupMenuItem(value: 'files', child: Text('文件管理')),
              if (widget.onOpenOrganization != null)
                const PopupMenuItem(value: 'org', child: Text('组织架构')),
              const PopupMenuItem(value: 'logout', child: Text('退出登录')),
            ],
          ),
        ],
      ),
      body: _buildBody(),
      floatingActionButton: FloatingActionButton(
        onPressed: widget.onOpenContacts,
        tooltip: '新建会话',
        child: const Icon(Icons.edit),
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.error_outline, size: 48, color: Theme.of(context).colorScheme.error),
            const SizedBox(height: 16),
            Text(_error!, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.tonal(
              onPressed: _loadSessions,
              child: const Text('重试'),
            ),
          ],
        ),
      );
    }

    if (_sessions.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.chat_bubble_outline, size: 64, color: Theme.of(context).colorScheme.outline),
            const SizedBox(height: 16),
            Text(
              '暂无会话',
              style: TextStyle(
                fontSize: 16,
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              '点击右下角按钮开始新对话',
              style: TextStyle(
                fontSize: 14,
                color: Theme.of(context).colorScheme.outline,
              ),
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: _loadSessions,
      child: ListView.separated(
        itemCount: _sessions.length,
        separatorBuilder: (context, index) => Divider(
          height: 1,
          indent: 72,
          color: Theme.of(context).colorScheme.outlineVariant,
        ),
        itemBuilder: (context, index) {
          final session = _sessions[index];
          return SessionItem(
            session: session,
            onTap: () => widget.onOpenChat(session),
          );
        },
      ),
    );
  }
}
