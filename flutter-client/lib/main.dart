import 'dart:async';

import 'package:flutter/material.dart';

import 'models/session.dart';
import 'models/user.dart';
import 'screens/chat_screen.dart';
import 'screens/contact_profile_screen.dart';
import 'screens/contacts_screen.dart';
import 'screens/file_manager_screen.dart';
import 'screens/friend_requests_screen.dart';
import 'screens/login_screen.dart';
import 'screens/organization_screen.dart';
import 'screens/search_screen.dart';
import 'screens/session_list_screen.dart';
import 'screens/settings_screen.dart';
import 'services/api_service.dart';
import 'services/socket_service.dart';
import 'services/storage_service.dart';

void main() {
  runApp(const EnterpriseImApp());
}

class EnterpriseImApp extends StatelessWidget {
  const EnterpriseImApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const AppRoot();
  }
}

enum _AppPhase { loading, login, onboarding, main }

class AppRoot extends StatefulWidget {
  const AppRoot({super.key});

  @override
  State<AppRoot> createState() => _AppRootState();
}

class _AppRootState extends State<AppRoot> {
  final storageService = StorageService();
  late final ApiService apiService;
  final socketService = SocketService();

  _AppPhase _phase = _AppPhase.loading;
  AppUser? _currentUser;
  bool _darkMode = false;

  @override
  void initState() {
    super.initState();
    apiService = ApiService(baseUrl: '');
    socketService.onStateChanged = (_, __) {
      if (mounted) setState(() {});
    };
    _initApp();
  }

  Future<void> _initApp() async {
    await storageService.init();

    _darkMode = storageService.getDarkMode();

    final token = storageService.getToken();
    final userId = storageService.getUserId();
    final userName = storageService.getUserName();
    final host = storageService.getHost();
    final httpPort = storageService.getHttpPort();
    final tcpPort = storageService.getTcpPort();

    if (token != null && token.isNotEmpty && userId != null && userId.isNotEmpty) {
      // Restore session
      apiService.token = token;
      apiService.currentUserId = userId;
      if (host != null && httpPort != null) {
        apiService.updateBaseUrl(host, httpPort);
      }

      _currentUser = AppUser(
        id: userId,
        name: userName ?? userId,
      );

      // Connect TCP socket
      if (host != null && tcpPort != null) {
        final port = int.tryParse(tcpPort);
        if (port != null) {
          unawaited(socketService.connect(host, port, userId, token));
        }
      }

      final onboardingComplete = storageService.prefs?.getBool('onboarding_complete') ?? false;
      if (!mounted) return;
      setState(() {
        _phase = onboardingComplete ? _AppPhase.main : _AppPhase.onboarding;
      });
    } else {
      if (!mounted) return;
      setState(() => _phase = _AppPhase.login);
    }
  }

  @override
  void dispose() {
    socketService.disconnect();
    storageService.close();
    super.dispose();
  }

  void _onLoginSuccess(AppUser user, String token) {
    _currentUser = user;
    apiService.token = token;
    apiService.currentUserId = user.id;

    // Connect TCP socket
    final host = storageService.getHost();
    final tcpPort = storageService.getTcpPort();
    if (host != null && tcpPort != null) {
      final port = int.tryParse(tcpPort);
      if (port != null) {
        unawaited(socketService.connect(host, port, user.id, token));
      }
    }

    final onboardingComplete = storageService.prefs?.getBool('onboarding_complete') ?? false;
    setState(() {
      _phase = onboardingComplete ? _AppPhase.main : _AppPhase.onboarding;
    });
  }

  void _onLogout() {
    socketService.disconnect();
    storageService.clearAuth();
    apiService.token = null;
    apiService.currentUserId = null;
    _currentUser = null;
    setState(() => _phase = _AppPhase.login);
  }

  void _onDarkModeChanged() {
    final newVal = storageService.getDarkMode();
    if (newVal != _darkMode) {
      setState(() => _darkMode = newVal);
    }
  }

  void _onOnboardingComplete() {
    storageService.prefs?.setBool('onboarding_complete', true);
    setState(() => _phase = _AppPhase.main);
  }

  @override
  Widget build(BuildContext context) {
    const seed = Color(0xFF2563EB);
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: '企业 IM',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: seed),
        scaffoldBackgroundColor: const Color(0xFFF6F8FB),
        useMaterial3: true,
      ),
      darkTheme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: seed,
          brightness: Brightness.dark,
        ),
        scaffoldBackgroundColor: const Color(0xFF1A1A2E),
        useMaterial3: true,
      ),
      themeMode: _darkMode ? ThemeMode.dark : ThemeMode.light,
      home: _buildHome(),
    );
  }

  Widget _buildHome() {
    switch (_phase) {
      case _AppPhase.loading:
        return const _LoadingScreen();
      case _AppPhase.login:
        return LoginScreen(
          apiService: apiService,
          storageService: storageService,
          onLoginSuccess: _onLoginSuccess,
        );
      case _AppPhase.onboarding:
        return _OnboardingPage(onComplete: _onOnboardingComplete);
      case _AppPhase.main:
        return _MainScaffold(
          apiService: apiService,
          socketService: socketService,
          storageService: storageService,
          currentUser: _currentUser ?? AppUser(id: '', name: ''),
          onLogout: _onLogout,
          onDarkModeChanged: _onDarkModeChanged,
        );
    }
  }
}

// ---------------------------------------------------------------------------
// _MainScaffold — bottom navigation scaffold
// ---------------------------------------------------------------------------

class _MainScaffold extends StatefulWidget {
  const _MainScaffold({
    required this.apiService,
    required this.socketService,
    required this.storageService,
    required this.currentUser,
    required this.onLogout,
    required this.onDarkModeChanged,
  });

  final ApiService apiService;
  final SocketService socketService;
  final StorageService storageService;
  final AppUser currentUser;
  final VoidCallback onLogout;
  final VoidCallback onDarkModeChanged;

  @override
  State<_MainScaffold> createState() => _MainScaffoldState();
}

class _MainScaffoldState extends State<_MainScaffold> {
  int _currentTab = 0;
  int _totalUnread = 0;
  int _pendingFriendRequests = 0;
  Timer? _unreadTimer;

  @override
  void initState() {
    super.initState();
    _loadUnreadCount();
    _unreadTimer = Timer.periodic(const Duration(seconds: 15), (_) => _loadUnreadCount());
  }

  @override
  void dispose() {
    _unreadTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadUnreadCount() async {
    try {
      final data = await widget.apiService.getConversations();
      final items = data['items'] ?? data['conversations'] ?? data['data'];
      if (items is List) {
        int total = 0;
        for (final item in items) {
          if (item is Map) {
            total += Session.fromJson(Map<String, Object?>.from(item)).unreadCount;
          }
        }
        if (mounted) setState(() => _totalUnread = total);
      }
    } catch (_) {
      // Silently ignore errors when loading unread badge.
    }
  }

  Future<void> _loadFriendRequestCount() async {
    try {
      final data = await widget.apiService.getFriendRequests();
      final items = data['items'];
      if (items is List && mounted) {
        setState(() => _pendingFriendRequests = items.length);
      }
    } catch (_) {
      // Silently ignore errors.
    }
  }

  void _onTabChanged(int index) {
    setState(() => _currentTab = index);
    if (index == 1) {
      _loadFriendRequestCount();
    }
    widget.onDarkModeChanged();
  }

  // --- Full-screen push helpers ---

  void _openChat(Session session) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ChatScreen(
          session: session,
          apiService: widget.apiService,
          socketService: widget.socketService,
          storageService: widget.storageService,
          currentUser: widget.currentUser,
          onBack: () => Navigator.of(context).pop(),
        ),
      ),
    );
  }

  void _openSearch() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => SearchScreen(
          apiService: widget.apiService,
          currentUserId: widget.currentUser.id,
          onBack: () => Navigator.of(context).pop(),
          onStartChat: (session) {
            Navigator.of(context).pop();
            _openChat(session);
          },
        ),
      ),
    );
  }

  void _openFileManager() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => FileManagerScreen(
          apiService: widget.apiService,
          currentUserId: widget.currentUser.id,
          onBack: () => Navigator.of(context).pop(),
        ),
      ),
    );
  }

  void _openOrganization() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => OrganizationScreen(
          apiService: widget.apiService,
          onBack: () => Navigator.of(context).pop(),
          onStartChat: (session) {
            Navigator.of(context).pop();
            _openChat(session);
          },
        ),
      ),
    );
  }

  void _openFriendRequests() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => FriendRequestsScreen(
          apiService: widget.apiService,
          onBack: () => Navigator.of(context).pop(),
        ),
      ),
    );
  }

  void _openContactProfile(String userId) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ContactProfileScreen(
          apiService: widget.apiService,
          currentUserId: widget.currentUser.id,
          userId: userId,
          onBack: () => Navigator.of(context).pop(),
          onStartChat: (session) {
            Navigator.of(context).pop();
            _openChat(session);
          },
        ),
      ),
    );
  }

  // --- build ---

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      body: Column(
        children: [
          _NetworkStatusBar(socketService: widget.socketService),
          Expanded(
            child: IndexedStack(
              index: _currentTab,
              children: [
                // Tab 0 – 消息
                SessionListScreen(
                  apiService: widget.apiService,
                  socketService: widget.socketService,
                  currentUser: widget.currentUser,
                  onOpenChat: _openChat,
                  onOpenContacts: () => _onTabChanged(1),
                  onLogout: widget.onLogout,
                  onOpenSettings: () => _onTabChanged(3),
                  onOpenSearch: _openSearch,
                  onOpenFileManager: _openFileManager,
                  onOpenOrganization: _openOrganization,
                ),
                // Tab 1 – 通讯录
                ContactsScreen(
                  apiService: widget.apiService,
                  currentUser: widget.currentUser,
                  onBack: () => _onTabChanged(0),
                  onStartChat: _openChat,
                  onOpenFriendRequests: _openFriendRequests,
                  onOpenContactProfile: _openContactProfile,
                ),
                // Tab 2 – 工作台
                _WorkbenchPage(
                  onOpenFileManager: _openFileManager,
                  onOpenOrganization: _openOrganization,
                  onOpenSearch: _openSearch,
                ),
                // Tab 3 – 我
                SettingsScreen(
                  apiService: widget.apiService,
                  storageService: widget.storageService,
                  currentUser: widget.currentUser,
                  onBack: () => _onTabChanged(0),
                  onLogout: widget.onLogout,
                ),
              ],
            ),
          ),
        ],
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentTab,
        onTap: _onTabChanged,
        type: BottomNavigationBarType.fixed,
        selectedItemColor: colorScheme.primary,
        unselectedItemColor: colorScheme.onSurfaceVariant,
        items: [
          BottomNavigationBarItem(
            icon: _totalUnread > 0
                ? Badge(
                    label: Text(_totalUnread > 99 ? '99+' : '$_totalUnread'),
                    child: const Icon(Icons.chat_bubble_outlined),
                  )
                : const Icon(Icons.chat_bubble_outlined),
            activeIcon: _totalUnread > 0
                ? Badge(
                    label: Text(_totalUnread > 99 ? '99+' : '$_totalUnread'),
                    child: const Icon(Icons.chat_bubble),
                  )
                : const Icon(Icons.chat_bubble),
            label: '消息',
          ),
          BottomNavigationBarItem(
            icon: _pendingFriendRequests > 0
                ? Badge(
                    label: Text('$_pendingFriendRequests'),
                    child: const Icon(Icons.contacts_outlined),
                  )
                : const Icon(Icons.contacts_outlined),
            activeIcon: _pendingFriendRequests > 0
                ? Badge(
                    label: Text('$_pendingFriendRequests'),
                    child: const Icon(Icons.contacts),
                  )
                : const Icon(Icons.contacts),
            label: '通讯录',
          ),
          const BottomNavigationBarItem(
            icon: Icon(Icons.workspaces_outlined),
            activeIcon: Icon(Icons.workspaces),
            label: '工作台',
          ),
          const BottomNavigationBarItem(
            icon: Icon(Icons.person_outlined),
            activeIcon: Icon(Icons.person),
            label: '我',
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// _NetworkStatusBar
// ---------------------------------------------------------------------------

class _NetworkStatusBar extends StatelessWidget {
  const _NetworkStatusBar({required this.socketService});

  final SocketService socketService;

  @override
  Widget build(BuildContext context) {
    final connected = socketService.connected;
    final authenticated = socketService.authenticated;

    final String text;
    final Color color;

    if (authenticated) {
      text = '已连接';
      color = const Color(0xFF16A34A);
    } else if (connected) {
      text = '连接中...';
      color = const Color(0xFFCA8A04);
    } else {
      text = '未连接';
      color = const Color(0xFFDC2626);
    }

    return Container(
      width: double.infinity,
      height: 24,
      color: color.withValues(alpha:0.12),
      alignment: Alignment.center,
      child: Text(
        text,
        style: TextStyle(fontSize: 11, color: color, fontWeight: FontWeight.w500),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// _WorkbenchPage — 工作台 placeholder
// ---------------------------------------------------------------------------

class _WorkbenchPage extends StatelessWidget {
  const _WorkbenchPage({
    required this.onOpenFileManager,
    required this.onOpenOrganization,
    required this.onOpenSearch,
  });

  final VoidCallback onOpenFileManager;
  final VoidCallback onOpenOrganization;
  final VoidCallback onOpenSearch;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('工作台'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _ItemCard(
            icon: Icons.folder_outlined,
            color: Theme.of(context).colorScheme.primary,
            title: '文件管理',
            subtitle: '管理上传的文件和附件',
            onTap: onOpenFileManager,
          ),
          const SizedBox(height: 12),
          _ItemCard(
            icon: Icons.business_outlined,
            color: const Color(0xFF7C3AED),
            title: '组织架构',
            subtitle: '查看公司组织架构和成员',
            onTap: onOpenOrganization,
          ),
          const SizedBox(height: 12),
          _ItemCard(
            icon: Icons.search,
            color: const Color(0xFF059669),
            title: '全局搜索',
            subtitle: '搜索消息、联系人和文件',
            onTap: onOpenSearch,
          ),
        ],
      ),
    );
  }
}

class _ItemCard extends StatelessWidget {
  const _ItemCard({
    required this.icon,
    required this.color,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final Color color;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: Theme.of(context).colorScheme.outlineVariant),
      ),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: color.withValues(alpha:0.1),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(icon, color: color),
        ),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Text(subtitle, style: const TextStyle(fontSize: 13)),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// _OnboardingPage — 3 onboarding slides with PageView
// ---------------------------------------------------------------------------

class _OnboardingPage extends StatefulWidget {
  const _OnboardingPage({required this.onComplete});

  final VoidCallback onComplete;

  @override
  State<_OnboardingPage> createState() => _OnboardingPageState();
}

class _OnboardingPageState extends State<_OnboardingPage> {
  final PageController _controller = PageController();
  int _currentPage = 0;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: PageView(
                controller: _controller,
                onPageChanged: (page) => setState(() => _currentPage = page),
                children: [
                  // Page 1 — 欢迎使用企业 IM
                  _buildSlide(
                    icon: Icons.business,
                    title: '欢迎使用企业 IM',
                    subtitle: '安全、高效的企业即时通讯平台',
                    color: colorScheme.primary,
                  ),
                  // Page 2 — 安全通讯
                  _buildSlide(
                    icon: Icons.lock_outline,
                    title: '安全通讯',
                    subtitle: '端到端加密，保障企业数据安全',
                    color: const Color(0xFF16A34A),
                  ),
                  // Page 3 — 开始使用
                  _buildSlide(
                    icon: Icons.rocket_launch_outlined,
                    title: '开始使用',
                    subtitle: '一切就绪，立即开启企业协作之旅',
                    color: const Color(0xFF7C3AED),
                    action: FilledButton(
                      onPressed: widget.onComplete,
                      child: const Text('立即开始'),
                    ),
                  ),
                ],
              ),
            ),
            // Page indicators
            Padding(
              padding: const EdgeInsets.only(bottom: 48),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(3, (index) {
                  final active = index == _currentPage;
                  return AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    margin: const EdgeInsets.symmetric(horizontal: 4),
                    width: active ? 24 : 8,
                    height: 8,
                    decoration: BoxDecoration(
                      color: active ? colorScheme.primary : colorScheme.outlineVariant,
                      borderRadius: BorderRadius.circular(4),
                    ),
                  );
                }),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSlide({
    required IconData icon,
    required String title,
    required String subtitle,
    required Color color,
    Widget? action,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 100,
            height: 100,
            decoration: BoxDecoration(
              color: color.withValues(alpha:0.1),
              shape: BoxShape.circle,
            ),
            child: Icon(icon, size: 48, color: color),
          ),
          const SizedBox(height: 32),
          Text(
            title,
            style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w700),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 12),
          Text(
            subtitle,
            style: TextStyle(
              fontSize: 16,
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
            textAlign: TextAlign.center,
          ),
          if (action != null) ...[
            const SizedBox(height: 32),
            action,
          ],
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// _LoadingScreen — unchanged from original
// ---------------------------------------------------------------------------

class _LoadingScreen extends StatelessWidget {
  const _LoadingScreen();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.business, size: 64, color: Color(0xFF2563EB)),
            SizedBox(height: 24),
            Text(
              '企业 IM',
              style: TextStyle(fontSize: 24, fontWeight: FontWeight.w700),
            ),
            SizedBox(height: 24),
            CircularProgressIndicator(),
          ],
        ),
      ),
    );
  }
}
