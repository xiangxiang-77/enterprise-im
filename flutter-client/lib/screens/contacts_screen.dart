import 'dart:async';

import 'package:flutter/material.dart';

import '../models/session.dart';
import '../models/user.dart';
import '../services/api_service.dart';

class ContactsScreen extends StatefulWidget {
  const ContactsScreen({
    super.key,
    required this.apiService,
    required this.currentUser,
    required this.onBack,
    required this.onStartChat,
    required this.onOpenFriendRequests,
    required this.onOpenContactProfile,
  });

  final ApiService apiService;
  final AppUser currentUser;
  final VoidCallback onBack;
  final void Function(Session session) onStartChat;
  final VoidCallback onOpenFriendRequests;
  final void Function(String userId) onOpenContactProfile;

  @override
  State<ContactsScreen> createState() => _ContactsScreenState();
}

class _ContactsScreenState extends State<ContactsScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  List<AppUser> _friends = [];
  List<AppUser> _directoryUsers = [];
  bool _loadingFriends = true;
  bool _loadingDirectory = true;
  String? _friendsError;
  String? _directoryError;

  final ScrollController _friendsScrollController = ScrollController();
  final ScrollController _directoryScrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    unawaited(_loadFriends());
    unawaited(_loadDirectory());
  }

  @override
  void dispose() {
    _tabController.dispose();
    _friendsScrollController.dispose();
    _directoryScrollController.dispose();
    super.dispose();
  }

  Future<void> _loadFriends() async {
    setState(() {
      _loadingFriends = true;
      _friendsError = null;
    });
    try {
      final data = await widget.apiService.getFriends();
      final items = _dataList(data);
      if (mounted) {
        setState(() {
          _friends = items.map((item) => AppUser.fromJson(item)).toList();
          _loadingFriends = false;
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _loadingFriends = false;
          _friendsError = '加载好友失败: $error';
        });
      }
    }
  }

  Future<void> _loadDirectory() async {
    setState(() {
      _loadingDirectory = true;
      _directoryError = null;
    });
    try {
      final data = await widget.apiService.getDirectoryUsers();
      final items = _dataList(data);
      if (mounted) {
        setState(() {
          _directoryUsers = items.map((item) => AppUser.fromJson(item)).toList();
          _loadingDirectory = false;
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _loadingDirectory = false;
          _directoryError = '加载用户目录失败: $error';
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

  String _firstLetterGroup(String name) {
    if (name.isEmpty) return '#';
    final first = name[0].toUpperCase();
    final code = first.codeUnitAt(0);
    if (code >= 65 && code <= 90) return first;
    return '#';
  }

  int _letterSortKey(String letter) {
    if (letter == '#') return 26;
    return letter.codeUnitAt(0) - 65;
  }

  String _formatLastSeen(int? lastSeenMs) {
    if (lastSeenMs == null || lastSeenMs <= 0) return '';
    final lastSeen = DateTime.fromMillisecondsSinceEpoch(lastSeenMs);
    final diff = DateTime.now().difference(lastSeen);
    if (diff.inMinutes < 1) return '刚刚离线';
    if (diff.inMinutes < 60) return '离线${diff.inMinutes}分钟';
    if (diff.inHours < 24) return '离线${diff.inHours}小时';
    if (diff.inDays < 365) return '离线${diff.inDays}天';
    return '离线很久';
  }

  static const List<String> _allLetters = [
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '#',
  ];

  void _startChatWith(AppUser user) {
    final session = Session(
      id: 'c_${widget.currentUser.id}_${user.id}',
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
        title: const Text('通讯录'),
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: '好友'),
            Tab(text: '用户目录'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildFriendsTab(colorScheme),
          _buildDirectoryTab(colorScheme),
        ],
      ),
    );
  }

  Widget _buildFriendsTab(ColorScheme colorScheme) {
    if (_loadingFriends) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_friendsError != null) {
      return _buildErrorView(_friendsError!, _loadFriends);
    }
    if (_friends.isEmpty) {
      return _buildEmptyView(Icons.people_outline, '暂无好友');
    }
    return _buildContactList(
      users: _friends,
      scrollController: _friendsScrollController,
      colorScheme: colorScheme,
      onRefresh: _loadFriends,
      countLabel: '联系人',
      header: ListTile(
        leading: CircleAvatar(
          backgroundColor: colorScheme.primaryContainer,
          child: Icon(Icons.person_add, color: colorScheme.onPrimaryContainer),
        ),
        title: const Text('新的朋友', style: TextStyle(fontWeight: FontWeight.w600)),
        trailing: const Icon(Icons.chevron_right),
        onTap: widget.onOpenFriendRequests,
      ),
    );
  }

  Widget _buildDirectoryTab(ColorScheme colorScheme) {
    if (_loadingDirectory) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_directoryError != null) {
      return _buildErrorView(_directoryError!, _loadDirectory);
    }
    if (_directoryUsers.isEmpty) {
      return _buildEmptyView(Icons.person_search, '暂无用户');
    }
    return _buildContactList(
      users: _directoryUsers,
      scrollController: _directoryScrollController,
      colorScheme: colorScheme,
      onRefresh: _loadDirectory,
      countLabel: '用户',
    );
  }

  Widget _buildContactList({
    required List<AppUser> users,
    required ScrollController scrollController,
    required ColorScheme colorScheme,
    required Future<void> Function() onRefresh,
    required String countLabel,
    Widget? header,
  }) {
    final sorted = List<AppUser>.from(users)..sort((a, b) => a.name.compareTo(b.name));

    final Map<String, List<AppUser>> groups = {};
    for (final user in sorted) {
      final letter = _firstLetterGroup(user.name);
      groups.putIfAbsent(letter, () => []).add(user);
    }
    final sortedLetters = groups.keys.toList()
      ..sort((a, b) => _letterSortKey(a).compareTo(_letterSortKey(b)));

    final letterKeys = <String, GlobalKey>{};
    for (final letter in sortedLetters) {
      letterKeys[letter] = GlobalKey();
    }

    final items = <Widget>[];

    // Contact count
    items.add(Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      color: colorScheme.surfaceContainerLow,
      child: Text(
        '共${users.length}位$countLabel',
        style: TextStyle(fontSize: 13, color: colorScheme.onSurfaceVariant),
      ),
    ));

    // Optional header (e.g. "新的朋友")
    if (header != null) {
      items.add(header);
    }

    // Letter groups with section headers
    for (final letter in sortedLetters) {
      items.add(Container(
        key: letterKeys[letter],
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        color: colorScheme.surfaceContainerHigh,
        child: Text(
          letter,
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w700,
            color: colorScheme.primary,
          ),
        ),
      ));
      final groupUsers = groups[letter]!;
      for (int i = 0; i < groupUsers.length; i++) {
        items.add(_buildUserTile(groupUsers[i], colorScheme));
        if (i < groupUsers.length - 1) {
          items.add(const Divider(height: 1, indent: 72));
        }
      }
    }

    final availableLetters = sortedLetters.toSet();

    return Stack(
      children: [
        RefreshIndicator(
          onRefresh: onRefresh,
          child: ListView(
            controller: scrollController,
            children: items,
          ),
        ),
        Positioned(
          right: 2,
          top: 0,
          bottom: 0,
          child: _buildLetterIndexBar(
            availableLetters: availableLetters,
            letterKeys: letterKeys,
            scrollController: scrollController,
            colorScheme: colorScheme,
          ),
        ),
      ],
    );
  }

  Widget _buildLetterIndexBar({
    required Set<String> availableLetters,
    required Map<String, GlobalKey> letterKeys,
    required ScrollController scrollController,
    required ColorScheme colorScheme,
  }) {
    return GestureDetector(
      onVerticalDragUpdate: (details) {
        const letterHeight = 18.0;
        final index = (details.localPosition.dy / letterHeight).floor();
        if (index >= 0 && index < _allLetters.length) {
          final letter = _allLetters[index];
          _scrollToLetter(letter, availableLetters, letterKeys);
        }
      },
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: _allLetters.map((letter) {
          final available = availableLetters.contains(letter);
          return GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: available
                ? () => _scrollToLetter(letter, availableLetters, letterKeys)
                : null,
            child: SizedBox(
              height: 18,
              width: 20,
              child: Center(
                child: Text(
                  letter,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: available
                        ? colorScheme.primary
                        : colorScheme.outline.withAlpha(100),
                  ),
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }

  void _scrollToLetter(
    String letter,
    Set<String> availableLetters,
    Map<String, GlobalKey> letterKeys,
  ) {
    if (!availableLetters.contains(letter)) return;
    final key = letterKeys[letter];
    if (key != null && key.currentContext != null) {
      Scrollable.ensureVisible(
        key.currentContext!,
        duration: const Duration(milliseconds: 200),
        alignment: 0.0,
      );
    }
  }

  Widget _buildUserTile(AppUser user, ColorScheme colorScheme) {
    final lastSeenText = user.online ? null : _formatLastSeen(user.lastSeen);
    return ListTile(
      leading: Stack(
        children: [
          CircleAvatar(
            backgroundColor: colorScheme.primaryContainer,
            child: Text(
              user.name.isNotEmpty ? user.name.substring(0, 1).toUpperCase() : 'U',
              style: TextStyle(
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
                border: Border.all(color: Colors.white, width: 2),
              ),
              child: const SizedBox(width: 12, height: 12),
            ),
          ),
        ],
      ),
      title: Text(
        user.name,
        style: const TextStyle(fontWeight: FontWeight.w600),
      ),
      subtitle: Text(
        lastSeenText ?? user.phone ?? user.id,
        style: TextStyle(color: colorScheme.onSurfaceVariant, fontSize: 13),
      ),
      trailing: IconButton(
        icon: const Icon(Icons.chat_bubble_outline),
        tooltip: '发起对话',
        onPressed: () => _startChatWith(user),
      ),
      onTap: () => widget.onOpenContactProfile(user.id),
    );
  }

  Widget _buildErrorView(String error, VoidCallback onRetry) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.error_outline, size: 48, color: Theme.of(context).colorScheme.error),
          const SizedBox(height: 16),
          Text(error, textAlign: TextAlign.center),
          const SizedBox(height: 16),
          FilledButton.tonal(onPressed: onRetry, child: const Text('重试')),
        ],
      ),
    );
  }

  Widget _buildEmptyView(IconData icon, String text) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 64, color: Theme.of(context).colorScheme.outline),
          const SizedBox(height: 16),
          Text(
            text,
            style: TextStyle(
              fontSize: 16,
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}
