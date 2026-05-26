import 'dart:async';

import 'package:flutter/material.dart';

import '../models/session.dart';
import '../services/api_service.dart';

class SearchScreen extends StatefulWidget {
  const SearchScreen({
    super.key,
    required this.apiService,
    required this.currentUserId,
    required this.onBack,
    required this.onStartChat,
  });

  final ApiService apiService;
  final String currentUserId;
  final VoidCallback onBack;
  final void Function(Session session) onStartChat;

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final TextEditingController _controller = TextEditingController();
  bool _searching = false;
  bool _searched = false;
  String? _error;

  // Grouped results
  List<Map<String, Object?>> _contacts = [];
  List<Map<String, Object?>> _groups = [];
  List<Map<String, Object?>> _messages = [];
  List<Map<String, Object?>> _files = [];

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _doSearch(String query) async {
    final q = query.trim();
    if (q.isEmpty) return;

    setState(() {
      _searching = true;
      _error = null;
      _searched = true;
      _contacts = [];
      _groups = [];
      _messages = [];
      _files = [];
    });

    try {
      final data = await widget.apiService.search(q, type: 'all');
      if (mounted) {
        setState(() {
          _contacts = _extractList(data, 'contacts');
          _groups = _extractList(data, 'groups');
          _messages = _extractList(data, 'messages');
          _files = _extractList(data, 'files');
          _searching = false;
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _searching = false;
          _error = '搜索失败: $error';
        });
      }
    }
  }

  List<Map<String, Object?>> _extractList(Map<String, Object?> data, String key) {
    final items = data[key];
    if (items is List) {
      return items.whereType<Map>().map((item) => Map<String, Object?>.from(item)).toList();
    }
    // Fallback: check items array with type field
    final allItems = data['items'];
    if (allItems is List) {
      return allItems
          .whereType<Map>()
          .where((item) => item['type']?.toString() == key)
          .map((item) => Map<String, Object?>.from(item))
          .toList();
    }
    return [];
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: widget.onBack,
        ),
        title: TextField(
          controller: _controller,
          autofocus: true,
          decoration: const InputDecoration(
            hintText: '搜索联系人、群组、消息...',
            border: InputBorder.none,
            hintStyle: TextStyle(color: Colors.grey),
          ),
          textInputAction: TextInputAction.search,
          onSubmitted: _doSearch,
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.search),
            tooltip: '搜索',
            onPressed: () => _doSearch(_controller.text),
          ),
        ],
      ),
      body: _buildBody(colorScheme),
    );
  }

  Widget _buildBody(ColorScheme colorScheme) {
    if (_searching) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.error_outline, size: 48, color: colorScheme.error),
            const SizedBox(height: 16),
            Text(_error!, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.tonal(
              onPressed: () => _doSearch(_controller.text),
              child: const Text('重试'),
            ),
          ],
        ),
      );
    }
    if (!_searched) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.search, size: 64, color: colorScheme.outline),
            const SizedBox(height: 16),
            Text(
              '输入关键词搜索',
              style: TextStyle(fontSize: 16, color: colorScheme.onSurfaceVariant),
            ),
          ],
        ),
      );
    }

    final totalResults =
        _contacts.length + _groups.length + _messages.length + _files.length;
    if (totalResults == 0) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.search_off, size: 64, color: colorScheme.outline),
            const SizedBox(height: 16),
            Text(
              '未找到相关结果',
              style: TextStyle(fontSize: 16, color: colorScheme.onSurfaceVariant),
            ),
          ],
        ),
      );
    }

    return ListView(
      children: [
        if (_contacts.isNotEmpty) _buildSection('联系人', Icons.person, _contacts, _buildContactItem),
        if (_groups.isNotEmpty) _buildSection('群组', Icons.group, _groups, _buildGroupItem),
        if (_messages.isNotEmpty) _buildSection('消息', Icons.chat_bubble_outline, _messages, _buildMessageItem),
        if (_files.isNotEmpty) _buildSection('文件', Icons.insert_drive_file, _files, _buildFileItem),
      ],
    );
  }

  Widget _buildSection(
    String title,
    IconData icon,
    List<Map<String, Object?>> items,
    Widget Function(Map<String, Object?>, ColorScheme) builder,
  ) {
    final colorScheme = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Row(
            children: [
              Icon(icon, size: 18, color: colorScheme.primary),
              const SizedBox(width: 8),
              Text(
                '$title (${items.length})',
                style: TextStyle(
                  fontWeight: FontWeight.w600,
                  color: colorScheme.primary,
                ),
              ),
            ],
          ),
        ),
        Container(
          color: colorScheme.surface,
          child: Column(
            children: [
              for (int i = 0; i < items.length; i++) ...[
                if (i > 0) const Divider(height: 1, indent: 56),
                builder(items[i], colorScheme),
              ],
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildContactItem(Map<String, Object?> item, ColorScheme colorScheme) {
    final name = item['name']?.toString() ?? '';
    final userId = item['id']?.toString() ?? item['userId']?.toString() ?? '';
    return ListTile(
      leading: CircleAvatar(
        backgroundColor: colorScheme.primaryContainer,
        child: Text(
          name.isNotEmpty ? name.substring(0, 1).toUpperCase() : 'U',
          style: TextStyle(
            fontWeight: FontWeight.w700,
            color: colorScheme.onPrimaryContainer,
          ),
        ),
      ),
      title: Text(name, style: const TextStyle(fontWeight: FontWeight.w600)),
      subtitle: Text(userId, style: TextStyle(fontSize: 13, color: colorScheme.onSurfaceVariant)),
      onTap: () {
        final session = Session(
          id: 'c_${widget.currentUserId}_$userId',
          name: name,
          peerId: userId,
        );
        widget.onStartChat(session);
      },
    );
  }

  Widget _buildGroupItem(Map<String, Object?> item, ColorScheme colorScheme) {
    final name = item['name']?.toString() ?? '';
    final groupId = item['id']?.toString() ?? '';
    final memberCount = item['memberCount']?.toString() ?? '';
    return ListTile(
      leading: CircleAvatar(
        backgroundColor: colorScheme.tertiaryContainer,
        child: Icon(Icons.group, color: colorScheme.onTertiaryContainer),
      ),
      title: Text(name, style: const TextStyle(fontWeight: FontWeight.w600)),
      subtitle: memberCount.isNotEmpty
          ? Text('$memberCount 人', style: TextStyle(fontSize: 13, color: colorScheme.onSurfaceVariant))
          : null,
      onTap: () {
        final session = Session(
          id: groupId,
          name: name,
          type: 'group',
        );
        widget.onStartChat(session);
      },
    );
  }

  Widget _buildMessageItem(Map<String, Object?> item, ColorScheme colorScheme) {
    final content = item['content']?.toString() ?? '';
    final senderName = item['senderName']?.toString() ?? '';
    final conversationId = item['conversationId']?.toString() ?? '';
    return ListTile(
      leading: CircleAvatar(
        backgroundColor: colorScheme.surfaceContainerHighest,
        child: Icon(Icons.chat_bubble_outline, color: colorScheme.onSurfaceVariant, size: 20),
      ),
      title: Text(
        content,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontSize: 14),
      ),
      subtitle: senderName.isNotEmpty
          ? Text(senderName, style: TextStyle(fontSize: 12, color: colorScheme.outline))
          : null,
      onTap: () {
        if (conversationId.isNotEmpty) {
          final session = Session(
            id: conversationId,
            name: senderName,
          );
          widget.onStartChat(session);
        }
      },
    );
  }

  Widget _buildFileItem(Map<String, Object?> item, ColorScheme colorScheme) {
    final fileName = item['fileName']?.toString() ?? item['name']?.toString() ?? '未知文件';
    final fileSize = item['fileSize']?.toString() ?? '';
    final senderName = item['senderName']?.toString() ?? '';
    return ListTile(
      leading: CircleAvatar(
        backgroundColor: colorScheme.surfaceContainerHighest,
        child: Icon(Icons.insert_drive_file, color: colorScheme.onSurfaceVariant, size: 20),
      ),
      title: Text(fileName, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
      subtitle: Text(
        [if (fileSize.isNotEmpty) _formatFileSize(fileSize), if (senderName.isNotEmpty) senderName]
            .join(' · '),
        style: TextStyle(fontSize: 12, color: colorScheme.outline),
      ),
      onTap: () {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('文件: $fileName')),
        );
      },
    );
  }

  String _formatFileSize(String sizeStr) {
    final size = int.tryParse(sizeStr);
    if (size == null) return sizeStr;
    if (size < 1024) return '$size B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)} KB';
    return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}
