import 'dart:async';

import 'package:flutter/material.dart';

import '../models/session.dart';
import '../models/user.dart';
import '../services/api_service.dart';

class ContactProfileScreen extends StatefulWidget {
  const ContactProfileScreen({
    super.key,
    required this.apiService,
    required this.currentUserId,
    required this.userId,
    required this.onBack,
    required this.onStartChat,
  });

  final ApiService apiService;
  final String currentUserId;
  final String userId;
  final VoidCallback onBack;
  final void Function(Session session) onStartChat;

  @override
  State<ContactProfileScreen> createState() => _ContactProfileScreenState();
}

class _ContactProfileScreenState extends State<ContactProfileScreen> {
  AppUser? _user;
  bool _loading = true;
  String? _error;
  bool _blacklisted = false;
  bool _actionLoading = false;

  @override
  void initState() {
    super.initState();
    unawaited(_loadUser());
    unawaited(_loadBlacklistStatus());
  }

  Future<void> _loadBlacklistStatus() async {
    try {
      final data = await widget.apiService.getBlacklist();
      if (!mounted) return;
      final items = data['items'];
      if (items is List) {
        for (final item in items) {
          if (item is Map) {
            final id = item['id']?.toString() ?? item['userId']?.toString() ?? '';
            if (id == widget.userId) {
              setState(() => _blacklisted = true);
              return;
            }
          }
        }
      }
    } catch (_) {
      // ignore
    }
  }

  Future<void> _loadUser() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await widget.apiService.getUserDetail(widget.userId);
      if (mounted) {
        setState(() {
          _user = AppUser.fromJson(data);
          _loading = false;
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = '加载用户信息失败: $error';
        });
      }
    }
  }

  Future<void> _showRemarkDialog() async {
    final controller = TextEditingController(text: _user?.name ?? '');
    final remark = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('设置备注'),
        content: TextField(
          controller: controller,
          decoration: const InputDecoration(
            labelText: '备注名称',
            hintText: '输入备注名',
            border: OutlineInputBorder(),
          ),
          autofocus: true,
          onSubmitted: (value) => Navigator.pop(context, value.trim()),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('保存'),
          ),
        ],
      ),
    );
    if (remark == null || remark.isEmpty || !mounted) return;
    setState(() => _actionLoading = true);
    try {
      await widget.apiService.updateFriendRemark(widget.userId, remark);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('备注已保存')),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('设置备注失败: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _actionLoading = false);
    }
  }

  Future<void> _toggleBlacklist() async {
    setState(() => _actionLoading = true);
    try {
      if (_blacklisted) {
        await widget.apiService.unblacklistUser(widget.userId);
        if (mounted) {
          setState(() => _blacklisted = false);
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('已移出黑名单')),
          );
        }
      } else {
        await widget.apiService.blacklistUser(widget.userId);
        if (mounted) {
          setState(() => _blacklisted = true);
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('已加入黑名单')),
          );
        }
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('操作失败: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _actionLoading = false);
    }
  }

  Future<void> _showDeleteConfirm() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('删除好友'),
        content: const Text('确定要删除该好友吗？此操作不可撤销。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _actionLoading = true);
    try {
      await widget.apiService.deleteFriend(widget.userId);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('好友已删除')),
        );
        widget.onBack();
      }
    } catch (error) {
      if (mounted) {
        setState(() => _actionLoading = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('删除失败: $error')),
        );
      }
    }
  }

  void _shareContact() {
    final user = _user;
    if (user == null) return;
    final shareText = '【名片】${user.name}\n'
        'ID: ${user.id}\n'
        '${user.phone != null && user.phone!.isNotEmpty ? '手机: ${user.phone}\n' : ''}'
        '${user.signature != null && user.signature!.isNotEmpty ? '签名: ${user.signature}\n' : ''}'
        '--- 来自企业IM ---';
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('名片已复制:\n$shareText'),
        duration: const Duration(seconds: 2),
      ),
    );
  }

  Future<void> _favoriteContact() async {
    setState(() => _actionLoading = true);
    try {
      await widget.apiService.favoriteContact(widget.userId);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('已收藏')),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('收藏失败: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _actionLoading = false);
    }
  }

  void _startChat() {
    final user = _user;
    if (user == null) return;
    final session = Session(
      id: 'c_${widget.currentUserId}_${user.id}',
      name: user.name,
      peerId: user.id,
      online: user.online,
    );
    widget.onStartChat(session);
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
        title: const Text('详细资料'),
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
            FilledButton.tonal(onPressed: _loadUser, child: const Text('重试')),
          ],
        ),
      );
    }
    final user = _user!;
    return ListView(
      children: [
        // User info card
        Container(
          color: colorScheme.surface,
          width: double.infinity,
          padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 20),
          child: Column(
            children: [
              // Avatar
              Stack(
                children: [
                  CircleAvatar(
                    radius: 44,
                    backgroundColor: colorScheme.primaryContainer,
                    child: Text(
                      user.name.isNotEmpty ? user.name.substring(0, 1).toUpperCase() : 'U',
                      style: TextStyle(
                        fontSize: 32,
                        fontWeight: FontWeight.w700,
                        color: colorScheme.onPrimaryContainer,
                      ),
                    ),
                  ),
                  Positioned(
                    right: 0,
                    bottom: 0,
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: user.online ? const Color(0xFF16A34A) : const Color(0xFF9E9E9E),
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 3),
                      ),
                      child: const SizedBox(width: 16, height: 16),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              // Name
              Text(
                user.name,
                style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 8),
              // ID / phone
              Text(
                'ID: ${user.id}',
                style: TextStyle(fontSize: 14, color: colorScheme.onSurfaceVariant),
              ),
              if (user.phone != null && user.phone!.isNotEmpty) ...[
                const SizedBox(height: 4),
                Text(
                  '手机: ${user.phone}',
                  style: TextStyle(fontSize: 14, color: colorScheme.onSurfaceVariant),
                ),
              ],
              const SizedBox(height: 8),
              // Online status
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: BoxDecoration(
                      color: user.online ? const Color(0xFF16A34A) : colorScheme.outline,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    user.online
                        ? '在线'
                        : user.lastSeen != null && user.lastSeen! > 0
                            ? _formatLastSeen(user.lastSeen)
                            : '离线',
                    style: TextStyle(fontSize: 13, color: colorScheme.onSurfaceVariant),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              // Extra profile fields
              _buildExtraInfoRow(colorScheme, user),
            ],
          ),
        ),

        const SizedBox(height: 12),

        // Action buttons
        Container(
          color: colorScheme.surface,
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          child: Column(
            children: [
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: _actionLoading ? null : _startChat,
                  icon: const Icon(Icons.chat),
                  label: const Text('发消息'),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(48),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('语音通话功能开发中')),
                        );
                      },
                      icon: const Icon(Icons.phone),
                      label: const Text('语音通话'),
                      style: OutlinedButton.styleFrom(
                        minimumSize: const Size.fromHeight(48),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('视频通话功能开发中')),
                        );
                      },
                      icon: const Icon(Icons.videocam),
                      label: const Text('视频通话'),
                      style: OutlinedButton.styleFrom(
                        minimumSize: const Size.fromHeight(48),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),

        const SizedBox(height: 12),

        // Friend management actions
        Container(
          color: colorScheme.surface,
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
          child: Column(
            children: [
              _buildActionTile(
                icon: Icons.edit_note,
                title: '设置备注',
                onTap: _actionLoading ? null : _showRemarkDialog,
                colorScheme: colorScheme,
              ),
              const Divider(height: 1, indent: 56),
              _buildActionTile(
                icon: _blacklisted ? Icons.person_off : Icons.block,
                title: _blacklisted ? '移出黑名单' : '加入黑名单',
                onTap: _actionLoading ? null : _toggleBlacklist,
                colorScheme: colorScheme,
              ),
              const Divider(height: 1, indent: 56),
              _buildActionTile(
                icon: Icons.person_remove,
                title: '删除好友',
                titleColor: colorScheme.error,
                onTap: _actionLoading ? null : _showDeleteConfirm,
                colorScheme: colorScheme,
              ),
            ],
          ),
        ),

        const SizedBox(height: 12),

        // Share / Favorite
        Container(
          color: colorScheme.surface,
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
          child: Column(
            children: [
              _buildActionTile(
                icon: Icons.share_outlined,
                title: '分享名片',
                onTap: _actionLoading ? null : _shareContact,
                colorScheme: colorScheme,
              ),
              const Divider(height: 1, indent: 56),
              _buildActionTile(
                icon: Icons.star_outline,
                title: '收藏名片',
                onTap: _actionLoading ? null : _favoriteContact,
                colorScheme: colorScheme,
              ),
            ],
          ),
        ),

        const SizedBox(height: 24),
      ],
    );
  }

  Widget _buildExtraInfoRow(ColorScheme colorScheme, AppUser user) {
    final items = <String>[];
    if (user.gender != null && user.gender!.isNotEmpty) {
      items.add('性别: ${user.gender}');
    }
    if (user.region != null && user.region!.isNotEmpty) {
      items.add('地区: ${user.region}');
    }
    if (user.signature != null && user.signature!.isNotEmpty) {
      items.add('签名: ${user.signature}');
    }
    if (user.source != null && user.source!.isNotEmpty) {
      items.add('好友来源: ${user.source}');
    }
    if (items.isEmpty) return const SizedBox.shrink();

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: items.map((text) => Padding(
          padding: const EdgeInsets.symmetric(vertical: 2),
          child: Text(
            text,
            style: TextStyle(fontSize: 13, color: colorScheme.onSurfaceVariant),
          ),
        )).toList(),
      ),
    );
  }

  Widget _buildActionTile({
    required IconData icon,
    required String title,
    required VoidCallback? onTap,
    required ColorScheme colorScheme,
    Color? titleColor,
  }) {
    return ListTile(
      leading: Icon(icon),
      title: Text(
        title,
        style: TextStyle(
          fontSize: 15,
          color: titleColor ?? colorScheme.onSurface,
        ),
      ),
      trailing: const Icon(Icons.chevron_right, size: 20),
      onTap: onTap,
      contentPadding: EdgeInsets.zero,
      visualDensity: VisualDensity.compact,
    );
  }

  String _formatLastSeen(int? lastSeenMs) {
    if (lastSeenMs == null || lastSeenMs <= 0) return '离线';
    final lastSeen = DateTime.fromMillisecondsSinceEpoch(lastSeenMs);
    final diff = DateTime.now().difference(lastSeen);
    if (diff.inMinutes < 1) return '刚刚离线';
    if (diff.inMinutes < 60) return '离线${diff.inMinutes}分钟';
    if (diff.inHours < 24) return '离线${diff.inHours}小时';
    if (diff.inDays < 365) return '离线${diff.inDays}天';
    return '离线很久';
  }
}
