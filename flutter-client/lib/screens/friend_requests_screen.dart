import 'dart:async';

import 'package:flutter/material.dart';

import '../services/api_service.dart';

class FriendRequest {
  FriendRequest({
    required this.id,
    required this.senderName,
    required this.senderId,
    this.message = '',
    this.status = 'pending',
    this.createdAt,
  });

  final String id;
  final String senderName;
  final String senderId;
  final String message;
  final String status;
  final DateTime? createdAt;

  factory FriendRequest.fromJson(Map<String, Object?> json) {
    return FriendRequest(
      id: json['id']?.toString() ?? '',
      senderName: json['senderName']?.toString() ??
          json['fromUserName']?.toString() ??
          json['requesterName']?.toString() ??
          '未知用户',
      senderId: json['senderId']?.toString() ??
          json['fromUserId']?.toString() ??
          json['requesterId']?.toString() ??
          '',
      message: json['message']?.toString() ?? json['verifyMessage']?.toString() ?? '',
      status: json['status']?.toString() ?? 'pending',
      createdAt: _parseTime(json['createdAt'] ?? json['created_at']),
    );
  }

  static DateTime? _parseTime(Object? value) {
    if (value == null) return null;
    if (value is DateTime) return value;
    return DateTime.tryParse(value.toString());
  }
}

class FriendRequestsScreen extends StatefulWidget {
  const FriendRequestsScreen({
    super.key,
    required this.apiService,
    required this.onBack,
  });

  final ApiService apiService;
  final VoidCallback onBack;

  @override
  State<FriendRequestsScreen> createState() => _FriendRequestsScreenState();
}

class _FriendRequestsScreenState extends State<FriendRequestsScreen> {
  List<FriendRequest> _requests = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    unawaited(_loadRequests());
  }

  Future<void> _loadRequests() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await widget.apiService.getFriendRequests();
      final items = _dataList(data);
      if (mounted) {
        setState(() {
          _requests = items.map((item) => FriendRequest.fromJson(item)).toList();
          _loading = false;
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = '加载好友请求失败: $error';
        });
      }
    }
  }

  List<Map<String, Object?>> _dataList(Map<String, Object?> data) {
    final items = data['items'];
    if (items is List) {
      return items.whereType<Map>().map((item) => Map<String, Object?>.from(item)).toList();
    }
    return [];
  }

  Future<void> _handleRequest(String requestId, bool accept) async {
    try {
      await widget.apiService.handleFriendRequest(requestId, accept);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(accept ? '已接受好友请求' : '已拒绝好友请求')),
        );
        await _loadRequests();
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('操作失败: $error')),
        );
      }
    }
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
        title: const Text('新的朋友'),
      ),
      body: _buildBody(colorScheme),
    );
  }

  Widget _buildBody(ColorScheme colorScheme) {
    if (_loading) {
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
            FilledButton.tonal(onPressed: _loadRequests, child: const Text('重试')),
          ],
        ),
      );
    }
    if (_requests.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.person_add_disabled, size: 64, color: colorScheme.outline),
            const SizedBox(height: 16),
            Text(
              '暂无好友请求',
              style: TextStyle(fontSize: 16, color: colorScheme.onSurfaceVariant),
            ),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _loadRequests,
      child: ListView.separated(
        itemCount: _requests.length,
        separatorBuilder: (context, index) => const Divider(height: 1, indent: 72),
        itemBuilder: (context, index) => _buildRequestTile(_requests[index], colorScheme),
      ),
    );
  }

  Widget _buildRequestTile(FriendRequest request, ColorScheme colorScheme) {
    final isPending = request.status == 'pending';
    final timeStr = request.createdAt != null ? _formatTime(request.createdAt!) : '';

    return ListTile(
      leading: CircleAvatar(
        backgroundColor: colorScheme.primaryContainer,
        child: Text(
          request.senderName.isNotEmpty ? request.senderName.substring(0, 1).toUpperCase() : 'U',
          style: TextStyle(
            fontWeight: FontWeight.w700,
            color: colorScheme.onPrimaryContainer,
          ),
        ),
      ),
      title: Text(request.senderName, style: const TextStyle(fontWeight: FontWeight.w600)),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (request.message.isNotEmpty) ...[
            const SizedBox(height: 2),
            Text(request.message, maxLines: 2, overflow: TextOverflow.ellipsis),
          ],
          if (timeStr.isNotEmpty) ...[
            const SizedBox(height: 2),
            Text(timeStr, style: TextStyle(fontSize: 12, color: colorScheme.outline)),
          ],
        ],
      ),
      trailing: isPending
          ? Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                OutlinedButton(
                  onPressed: () => _handleRequest(request.id, false),
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size(56, 32),
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                  ),
                  child: const Text('拒绝', style: TextStyle(fontSize: 13)),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: () => _handleRequest(request.id, true),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size(56, 32),
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                  ),
                  child: const Text('接受', style: TextStyle(fontSize: 13)),
                ),
              ],
            )
          : Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                request.status == 'accepted' ? '已接受' : '已拒绝',
                style: TextStyle(fontSize: 12, color: colorScheme.onSurfaceVariant),
              ),
            ),
      isThreeLine: request.message.isNotEmpty,
    );
  }

  String _formatTime(DateTime time) {
    final now = DateTime.now();
    final diff = now.difference(time);
    if (diff.inDays > 0) return '${diff.inDays}天前';
    if (diff.inHours > 0) return '${diff.inHours}小时前';
    if (diff.inMinutes > 0) return '${diff.inMinutes}分钟前';
    return '刚刚';
  }
}
