import 'dart:async';

import 'package:flutter/material.dart';

import '../models/user.dart';
import '../services/api_service.dart';
import '../services/storage_service.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({
    super.key,
    required this.apiService,
    required this.storageService,
    required this.onLoginSuccess,
  });

  final ApiService apiService;
  final StorageService storageService;
  final void Function(AppUser user, String token) onLoginSuccess;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _phoneController = TextEditingController();
  final _passwordController = TextEditingController();
  final _smsController = TextEditingController();
  final _hostController = TextEditingController(text: '127.0.0.1');
  final _httpPortController = TextEditingController(text: '18080');
  final _tcpPortController = TextEditingController(text: '19090');

  bool _loading = false;
  bool _obscurePassword = true;
  String? _error;
  int _smsCountdown = 0;
  Timer? _smsTimer;

  @override
  void initState() {
    super.initState();
    _loadSavedConfig();
  }

  void _loadSavedConfig() {
    final host = widget.storageService.getHost();
    final httpPort = widget.storageService.getHttpPort();
    final tcpPort = widget.storageService.getTcpPort();
    if (host != null && host.isNotEmpty) _hostController.text = host;
    if (httpPort != null && httpPort.isNotEmpty) _httpPortController.text = httpPort;
    if (tcpPort != null && tcpPort.isNotEmpty) _tcpPortController.text = tcpPort;
  }

  @override
  void dispose() {
    _phoneController.dispose();
    _passwordController.dispose();
    _smsController.dispose();
    _hostController.dispose();
    _httpPortController.dispose();
    _tcpPortController.dispose();
    _smsTimer?.cancel();
    super.dispose();
  }

  Future<void> _login() async {
    final phone = _phoneController.text.trim();
    final password = _passwordController.text.trim();
    if (phone.isEmpty || password.isEmpty) {
      setState(() => _error = '请输入手机号和密码');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final host = _hostController.text.trim();
      final httpPort = _httpPortController.text.trim();
      final tcpPort = _tcpPortController.text.trim();

      widget.apiService.updateBaseUrl(host, httpPort);
      await widget.storageService.saveServerConfig(host, httpPort, tcpPort);

      final data = await widget.apiService.login(phone, password);
      final token = data['token']?.toString() ?? '';
      final userData = data['user'];
      final AppUser user;

      if (userData is Map<String, Object?>) {
        user = AppUser.fromJson(userData);
      } else {
        user = AppUser(
          id: data['userId']?.toString() ?? 'u_$phone',
          name: data['displayName']?.toString() ?? phone,
        );
      }

      if (token.isEmpty || user.id.isEmpty) {
        throw Exception('登录返回数据不完整');
      }

      widget.apiService.token = token;
      await widget.storageService.saveAuth(token, user.id, user.name);

      if (mounted) {
        widget.onLoginSuccess(user, token);
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = '登录失败: $error';
        });
      }
    }
  }

  void _sendSmsCode() async {
    if (_smsCountdown > 0) return;
    final phone = _phoneController.text.trim();
    if (phone.isEmpty) {
      setState(() => _error = '请先输入手机号');
      return;
    }

    setState(() {
      _smsCountdown = 60;
      _error = null;
    });

    try {
      final host = _hostController.text.trim();
      final httpPort = _httpPortController.text.trim();
      widget.apiService.updateBaseUrl(host, httpPort);
      await widget.apiService.sendSmsCode(phone);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('验证码已发送')),
        );
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _smsCountdown = 0;
          _error = '发送验证码失败: $error';
        });
      }
      return;
    }

    _smsTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_smsCountdown <= 1) {
        timer.cancel();
        setState(() => _smsCountdown = 0);
      } else {
        setState(() => _smsCountdown--);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 60),
              Icon(Icons.business, size: 64, color: colorScheme.primary),
              const SizedBox(height: 16),
              Text(
                '企业 IM',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                      color: colorScheme.onSurface,
                    ),
              ),
              const SizedBox(height: 6),
              Text(
                '安全高效的团队沟通工具',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: colorScheme.onSurfaceVariant,
                    ),
              ),
              const SizedBox(height: 48),
              ExpansionTile(
                tilePadding: EdgeInsets.zero,
                title: Text(
                  '服务器设置',
                  style: TextStyle(fontSize: 14, color: colorScheme.onSurfaceVariant),
                ),
                children: [
                  _buildField('服务器地址', _hostController, TextInputType.url),
                  Row(
                    children: [
                      Expanded(child: _buildField('HTTP 端口', _httpPortController, TextInputType.number)),
                      const SizedBox(width: 12),
                      Expanded(child: _buildField('TCP 端口', _tcpPortController, TextInputType.number)),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 8),
              _buildField('手机号', _phoneController, TextInputType.phone),
              const SizedBox(height: 16),
              TextField(
                controller: _passwordController,
                obscureText: _obscurePassword,
                keyboardType: TextInputType.visiblePassword,
                textInputAction: TextInputAction.done,
                onSubmitted: (_) => _login(),
                decoration: InputDecoration(
                  labelText: '密码',
                  prefixIcon: const Icon(Icons.lock_outline),
                  suffixIcon: IconButton(
                    icon: Icon(_obscurePassword ? Icons.visibility_off : Icons.visibility),
                    onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                  ),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                  filled: true,
                  fillColor: colorScheme.surfaceContainerLowest,
                ),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(child: _buildField('短信验证码', _smsController, TextInputType.number)),
                  const SizedBox(width: 12),
                  SizedBox(
                    height: 48,
                    child: OutlinedButton(
                      onPressed: _smsCountdown > 0 ? null : _sendSmsCode,
                      child: Text(_smsCountdown > 0 ? '${_smsCountdown}s' : '获取验证码'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: colorScheme.errorContainer,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Text(
                        _error!,
                        style: TextStyle(color: colorScheme.onErrorContainer, fontSize: 14),
                      ),
                    ),
                  ),
                ),
              FilledButton(
                onPressed: _loading ? null : _login,
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(52),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                child: _loading
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Text('登录', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildField(String label, TextEditingController controller, TextInputType keyboardType) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: TextField(
        controller: controller,
        keyboardType: keyboardType,
        decoration: InputDecoration(
          labelText: label,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          filled: true,
          fillColor: Theme.of(context).colorScheme.surfaceContainerLowest,
        ),
      ),
    );
  }
}
