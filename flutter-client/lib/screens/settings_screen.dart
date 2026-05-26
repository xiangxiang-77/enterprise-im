import 'package:flutter/material.dart';

import '../models/user.dart';
import '../services/api_service.dart';
import '../services/storage_service.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({
    super.key,
    required this.apiService,
    required this.storageService,
    required this.currentUser,
    required this.onBack,
    required this.onLogout,
  });

  final ApiService apiService;
  final StorageService storageService;
  final AppUser currentUser;
  final VoidCallback onBack;
  final VoidCallback onLogout;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  // Server config controllers
  late TextEditingController _hostController;
  late TextEditingController _httpPortController;
  late TextEditingController _tcpPortController;

  // Settings state
  String _fontSize = 'standard';
  bool _darkMode = false;
  bool _serverConfigExpanded = false;

  // Notification settings state
  bool _notifMessage = true;
  bool _notifAt = true;
  bool _notifRecall = true;
  bool _notifScreenshot = false;
  bool _notificationSaving = false;

  // DND settings state
  TimeOfDay? _dndStartTime;
  TimeOfDay? _dndEndTime;
  bool _dndSaving = false;

  @override
  void initState() {
    super.initState();
    _hostController = TextEditingController(text: widget.storageService.getHost() ?? '');
    _httpPortController = TextEditingController(text: widget.storageService.getHttpPort() ?? '');
    _tcpPortController = TextEditingController(text: widget.storageService.getTcpPort() ?? '');
    _fontSize = widget.storageService.getFontSize();
    _darkMode = widget.storageService.getDarkMode();
    _notifMessage = widget.storageService.getNotifMessageEnabled();
    _notifAt = widget.storageService.getNotifAtEnabled();
    _notifRecall = widget.storageService.getNotifRecallEnabled();
    _notifScreenshot = widget.storageService.getNotifScreenshotEnabled();
    _loadNotificationSettings();
  }

  @override
  void dispose() {
    _hostController.dispose();
    _httpPortController.dispose();
    _tcpPortController.dispose();
    super.dispose();
  }

  Future<void> _saveServerConfig() async {
    final host = _hostController.text.trim();
    final httpPort = _httpPortController.text.trim();
    final tcpPort = _tcpPortController.text.trim();

    if (host.isEmpty || httpPort.isEmpty || tcpPort.isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('请填写完整的服务器配置')),
        );
      }
      return;
    }

    await widget.storageService.saveServerConfig(host, httpPort, tcpPort);
    widget.apiService.updateBaseUrl(host, httpPort);

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('服务器配置已保存')),
      );
    }
  }

  Future<void> _saveFontSize(String? size) async {
    if (size == null) return;
    setState(() => _fontSize = size);
    await widget.storageService.saveFontSize(size);
  }

  Future<void> _saveDarkMode(bool value) async {
    setState(() => _darkMode = value);
    await widget.storageService.saveDarkMode(value);
  }

  Future<void> _loadNotificationSettings() async {
    try {
      final data = await widget.apiService.getNotificationSettings();
      if (!mounted) return;
      setState(() {
        _notifMessage = data['newMessage'] == true;
        _notifAt = data['mentionAlert'] == true;
        _notifRecall = data['recallNotice'] == true;
        _notifScreenshot = data['screenshotNotice'] == true;
        // DND time range
        final dndStart = data['dndStartTime']?.toString();
        final dndEnd = data['dndEndTime']?.toString();
        if (dndStart != null && dndStart.isNotEmpty) {
          final parts = dndStart.split(':');
          if (parts.length == 2) {
            _dndStartTime = TimeOfDay(
              hour: int.tryParse(parts[0]) ?? 22,
              minute: int.tryParse(parts[1]) ?? 0,
            );
          }
        }
        if (dndEnd != null && dndEnd.isNotEmpty) {
          final parts = dndEnd.split(':');
          if (parts.length == 2) {
            _dndEndTime = TimeOfDay(
              hour: int.tryParse(parts[0]) ?? 7,
              minute: int.tryParse(parts[1]) ?? 0,
            );
          }
        }
      });
      await widget.storageService.saveNotifMessageEnabled(_notifMessage);
      await widget.storageService.saveNotifAtEnabled(_notifAt);
      await widget.storageService.saveNotifRecallEnabled(_notifRecall);
      await widget.storageService.saveNotifScreenshotEnabled(_notifScreenshot);
    } catch (_) {
      // Keep local cached values when offline.
    }
  }

  Future<void> _saveNotificationSetting(String key, bool value) async {
    setState(() {
      _notificationSaving = true;
      if (key == 'newMessage') _notifMessage = value;
      if (key == 'mentionAlert') _notifAt = value;
      if (key == 'recallNotice') _notifRecall = value;
      if (key == 'screenshotNotice') _notifScreenshot = value;
    });
    try {
      final data = await widget.apiService.updateNotificationSettings({key: value});
      if (!mounted) return;
      setState(() {
        _notifMessage = data['newMessage'] == true;
        _notifAt = data['mentionAlert'] == true;
        _notifRecall = data['recallNotice'] == true;
        _notifScreenshot = data['screenshotNotice'] == true;
        _notificationSaving = false;
      });
      await widget.storageService.saveNotifMessageEnabled(_notifMessage);
      await widget.storageService.saveNotifAtEnabled(_notifAt);
      await widget.storageService.saveNotifRecallEnabled(_notifRecall);
      await widget.storageService.saveNotifScreenshotEnabled(_notifScreenshot);
    } catch (error) {
      if (!mounted) return;
      setState(() => _notificationSaving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('通知设置保存失败: $error')),
      );
    }
  }

  void _showLogoutDialog() {
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('退出登录'),
        content: const Text('确定要退出登录吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              widget.onLogout();
            },
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            child: const Text('退出'),
          ),
        ],
      ),
    );
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
        title: const Text('设置'),
      ),
      body: ListView(
        children: [
          // User info section
          _buildUserInfoSection(colorScheme),

          const SizedBox(height: 8),

          // Server configuration
          _buildServerConfigSection(colorScheme),

          const SizedBox(height: 8),

          // Display settings
          _buildDisplaySection(colorScheme),

          const SizedBox(height: 8),

          _buildNotificationSection(colorScheme),

          const SizedBox(height: 8),

          _buildDndSection(colorScheme),

          const SizedBox(height: 8),

          // About section
          _buildAboutSection(colorScheme),

          const SizedBox(height: 8),

          // Logout button
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: FilledButton.icon(
              onPressed: _showLogoutDialog,
              icon: const Icon(Icons.logout),
              label: const Text('退出登录'),
              style: FilledButton.styleFrom(
                backgroundColor: colorScheme.error,
                minimumSize: const Size.fromHeight(48),
              ),
            ),
          ),

          const SizedBox(height: 32),
        ],
      ),
    );
  }

  Widget _buildUserInfoSection(ColorScheme colorScheme) {
    return Container(
      color: colorScheme.surface,
      padding: const EdgeInsets.all(20),
      child: Row(
        children: [
          CircleAvatar(
            radius: 32,
            backgroundColor: colorScheme.primaryContainer,
            child: Text(
              widget.currentUser.name.isNotEmpty
                  ? widget.currentUser.name.substring(0, 1).toUpperCase()
                  : 'U',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.w700,
                color: colorScheme.onPrimaryContainer,
              ),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.currentUser.name,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'ID: ${widget.currentUser.id}',
                  style: TextStyle(
                    fontSize: 14,
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
                if (widget.currentUser.phone != null &&
                    widget.currentUser.phone!.isNotEmpty)
                  Text(
                    '手机: ${widget.currentUser.phone}',
                    style: TextStyle(
                      fontSize: 14,
                      color: colorScheme.onSurfaceVariant,
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildServerConfigSection(ColorScheme colorScheme) {
    return Container(
      color: colorScheme.surface,
      child: ExpansionTile(
        leading: const Icon(Icons.dns_outlined),
        title: const Text('服务器配置'),
        subtitle: Text(
          '${widget.storageService.getHost() ?? '未配置'}:${widget.storageService.getHttpPort() ?? ''}',
          style: TextStyle(fontSize: 13, color: colorScheme.onSurfaceVariant),
        ),
        initiallyExpanded: _serverConfigExpanded,
        onExpansionChanged: (expanded) {
          setState(() => _serverConfigExpanded = expanded);
        },
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            child: Column(
              children: [
                TextField(
                  controller: _hostController,
                  decoration: const InputDecoration(
                    labelText: '服务器地址',
                    hintText: '例如: 192.168.1.100',
                    border: OutlineInputBorder(),
                    contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                  ),
                  keyboardType: TextInputType.url,
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _httpPortController,
                        decoration: const InputDecoration(
                          labelText: 'HTTP 端口',
                          hintText: '例如: 18080',
                          border: OutlineInputBorder(),
                          contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                        ),
                        keyboardType: TextInputType.number,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: TextField(
                        controller: _tcpPortController,
                        decoration: const InputDecoration(
                          labelText: 'TCP 端口',
                          hintText: '例如: 19090',
                          border: OutlineInputBorder(),
                          contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                        ),
                        keyboardType: TextInputType.number,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: _saveServerConfig,
                    child: const Text('保存配置'),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDisplaySection(ColorScheme colorScheme) {
    return Container(
      color: colorScheme.surface,
      child: Column(
        children: [
          // ignore: prefer_const_constructors
          ListTile(
            leading: const Icon(Icons.text_fields),
            title: const Text('字体大小'),
            trailing: DropdownButton<String>(
              value: _fontSize,
              underline: const SizedBox(),
              items: const [
                DropdownMenuItem(value: 'small', child: Text('小')),
                DropdownMenuItem(value: 'standard', child: Text('标准')),
                DropdownMenuItem(value: 'large', child: Text('大')),
              ],
              onChanged: _saveFontSize,
            ),
          ),
          const Divider(height: 1, indent: 56),
          SwitchListTile(
            secondary: const Icon(Icons.dark_mode_outlined),
            title: const Text('深色模式'),
            subtitle: const Text('本地切换，重启后保持'),
            value: _darkMode,
            onChanged: _saveDarkMode,
          ),
        ],
      ),
    );
  }

  Widget _buildNotificationSection(ColorScheme colorScheme) {
    return Container(
      color: colorScheme.surface,
      child: Column(
        children: [
          ListTile(
            leading: const Icon(Icons.notifications_outlined),
            title: const Text('通知设置'),
            subtitle: Text(
              _notificationSaving ? '正在同步到服务器' : '服务器同步，离线时使用本地缓存',
              style: TextStyle(fontSize: 13, color: colorScheme.onSurfaceVariant),
            ),
          ),
          const Divider(height: 1, indent: 56),
          SwitchListTile(
            secondary: const Icon(Icons.chat_bubble_outline),
            title: const Text('新消息提醒'),
            value: _notifMessage,
            onChanged: _notificationSaving
                ? null
                : (value) => _saveNotificationSetting('newMessage', value),
          ),
          const Divider(height: 1, indent: 56),
          SwitchListTile(
            secondary: const Icon(Icons.alternate_email),
            title: const Text('@我提醒'),
            value: _notifAt,
            onChanged: _notificationSaving
                ? null
                : (value) => _saveNotificationSetting('mentionAlert', value),
          ),
          const Divider(height: 1, indent: 56),
          SwitchListTile(
            secondary: const Icon(Icons.undo),
            title: const Text('撤回提醒'),
            value: _notifRecall,
            onChanged: _notificationSaving
                ? null
                : (value) => _saveNotificationSetting('recallNotice', value),
          ),
          const Divider(height: 1, indent: 56),
          SwitchListTile(
            secondary: const Icon(Icons.screenshot_monitor),
            title: const Text('截屏提醒'),
            value: _notifScreenshot,
            onChanged: _notificationSaving
                ? null
                : (value) => _saveNotificationSetting('screenshotNotice', value),
          ),
        ],
      ),
    );
  }

  Widget _buildDndSection(ColorScheme colorScheme) {
    return Container(
      color: colorScheme.surface,
      child: Column(
        children: [
          ListTile(
            leading: const Icon(Icons.nights_stay_outlined),
            title: const Text('勿扰模式 (DND)'),
            subtitle: Text(
              _dndSaving ? '正在保存' : '设定免打扰时间段',
              style: TextStyle(fontSize: 13, color: colorScheme.onSurfaceVariant),
            ),
          ),
          const Divider(height: 1, indent: 56),
          ListTile(
            leading: const Icon(Icons.bedtime_outlined),
            title: const Text('开始时间'),
            trailing: Text(
              _dndStartTime != null
                  ? '${_dndStartTime!.hour.toString().padLeft(2, '0')}:${_dndStartTime!.minute.toString().padLeft(2, '0')}'
                  : '未设置',
              style: TextStyle(
                fontSize: 15,
                color: _dndStartTime != null ? colorScheme.onSurface : colorScheme.outline,
              ),
            ),
            onTap: _dndSaving ? null : () => _pickDndTime(isStart: true),
          ),
          const Divider(height: 1, indent: 56),
          ListTile(
            leading: const Icon(Icons.wb_sunny_outlined),
            title: const Text('结束时间'),
            trailing: Text(
              _dndEndTime != null
                  ? '${_dndEndTime!.hour.toString().padLeft(2, '0')}:${_dndEndTime!.minute.toString().padLeft(2, '0')}'
                  : '未设置',
              style: TextStyle(
                fontSize: 15,
                color: _dndEndTime != null ? colorScheme.onSurface : colorScheme.outline,
              ),
            ),
            onTap: _dndSaving ? null : () => _pickDndTime(isStart: false),
          ),
          const Divider(height: 1, indent: 56),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: _dndSaving ? null : _saveDndSettings,
                icon: _dndSaving
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Icon(Icons.save),
                label: Text(_dndSaving ? '保存中' : '保存免打扰时段'),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _pickDndTime({required bool isStart}) async {
    final initial = isStart
        ? (_dndStartTime ?? const TimeOfDay(hour: 22, minute: 0))
        : (_dndEndTime ?? const TimeOfDay(hour: 7, minute: 0));
    final picked = await showTimePicker(
      context: context,
      initialTime: initial,
      helpText: isStart ? '选择免打扰开始时间' : '选择免打扰结束时间',
      cancelText: '取消',
      confirmText: '确定',
    );
    if (picked == null || !mounted) return;
    setState(() {
      if (isStart) {
        _dndStartTime = picked;
      } else {
        _dndEndTime = picked;
      }
    });
  }

  Future<void> _saveDndSettings() async {
    final start = _dndStartTime;
    final end = _dndEndTime;
    if (start == null || end == null) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('请设置完整的免打扰时段')),
        );
      }
      return;
    }
    setState(() => _dndSaving = true);
    try {
      final startStr = '${start.hour.toString().padLeft(2, '0')}:${start.minute.toString().padLeft(2, '0')}';
      final endStr = '${end.hour.toString().padLeft(2, '0')}:${end.minute.toString().padLeft(2, '0')}';
      await widget.apiService.updateNotificationSettings({
        'dndStartTime': startStr,
        'dndEndTime': endStr,
      });
      if (mounted) {
        setState(() => _dndSaving = false);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('免打扰时段已保存')),
        );
      }
    } catch (error) {
      if (mounted) {
        setState(() => _dndSaving = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('保存失败: $error')),
        );
      }
    }
  }

  Widget _buildAboutSection(ColorScheme colorScheme) {
    return Container(
      color: colorScheme.surface,
      child: Column(
        children: [
          ListTile(
            leading: const Icon(Icons.info_outline),
            title: const Text('关于'),
            subtitle: const Text('企业 IM 移动端'),
            trailing: Text(
              'v1.0.0',
              style: TextStyle(
                fontSize: 14,
                color: colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          const Divider(height: 1, indent: 56),
          // ignore: prefer_const_constructors
          ListTile(
            leading: const Icon(Icons.code),
            title: const Text('技术栈'),
            subtitle: const Text('Flutter + Dart'),
          ),
        ],
      ),
    );
  }
}
