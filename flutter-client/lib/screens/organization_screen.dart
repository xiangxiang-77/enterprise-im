import 'package:flutter/material.dart';

import '../models/session.dart';
import '../services/api_service.dart';

class OrganizationScreen extends StatefulWidget {
  const OrganizationScreen({
    super.key,
    required this.apiService,
    required this.onBack,
    required this.onStartChat,
  });

  final ApiService apiService;
  final VoidCallback onBack;
  final void Function(Session session) onStartChat;

  @override
  State<OrganizationScreen> createState() => _OrganizationScreenState();
}

class _OrganizationScreenState extends State<OrganizationScreen> {
  List<Map<String, Object?>> _enterprises = [];
  Map<String, List<Map<String, Object?>>> _departments = {};
  Map<String, List<Map<String, Object?>>> _users = {};
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final enterprises = _dataList(await widget.apiService.get('/api/directory/enterprises'));
      final deptData = await widget.apiService.get('/api/directory/departments');
      final departments = _dataList(deptData);
      final userData = await widget.apiService.get('/api/directory/users');
      final users = _dataList(userData);

      final deptByEnterprise = <String, List<Map<String, Object?>>>{};
      for (final d in departments) {
        final eid = d['enterprise_id']?.toString() ?? '';
        deptByEnterprise.putIfAbsent(eid, () => []).add(d);
      }

      final usersByDept = <String, List<Map<String, Object?>>>{};
      for (final u in users) {
        final did = u['department_id']?.toString() ?? u['enterprise_id']?.toString() ?? '';
        usersByDept.putIfAbsent(did, () => []).add(u);
      }

      if (mounted) {
        setState(() {
          _enterprises = enterprises;
          _departments = deptByEnterprise;
          _users = usersByDept;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) setState(() { _loading = false; _error = e.toString(); });
    }
  }

  List<Map<String, Object?>> _dataList(Map<String, Object?> data) {
    final items = data['items'];
    if (items is List) {
      return items.whereType<Map>().map((item) => Map<String, Object?>.from(item)).toList();
    }
    return [];
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: widget.onBack),
        title: const Text('组织架构'),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text('加载失败: $_error'))
              : ListView(
                  children: _enterprises.map((enterprise) {
                    final eid = enterprise['id']?.toString() ?? '';
                    final ename = enterprise['name']?.toString() ?? eid;
                    final depts = _departments[eid] ?? [];
                    return ExpansionTile(
                      leading: const Icon(Icons.business),
                      title: Text(ename, style: const TextStyle(fontWeight: FontWeight.w600)),
                      subtitle: Text('${enterprise['code']?.toString() ?? ''} · ${depts.length}个部门'),
                      children: depts.map((dept) {
                        final did = dept['id']?.toString() ?? '';
                        final dname = dept['name']?.toString() ?? did;
                        final deptUsers = _users[did] ?? [];
                        return ExpansionTile(
                          tilePadding: const EdgeInsets.only(left: 32, right: 16),
                          leading: const Icon(Icons.folder_outlined),
                          title: Text(dname),
                          subtitle: Text('${deptUsers.length}人'),
                          children: deptUsers.map((user) {
                            final uid = user['id']?.toString() ?? '';
                            final uname = user['display_name']?.toString() ?? uid;
                            final avatar = user['avatar_url']?.toString();
                            return ListTile(
                              leading: CircleAvatar(
                                backgroundImage: avatar != null && avatar.isNotEmpty ? NetworkImage(avatar) : null,
                                child: avatar == null || avatar.isEmpty ? Text(uname[0].toUpperCase()) : null,
                              ),
                              title: Text(uname),
                              subtitle: Text(uid),
                              onTap: () {
                                widget.onStartChat(Session(
                                  id: 'single_$uid',
                                  name: uname,
                                  type: 'single',
                                  peerId: uid,
                                ));
                              },
                            );
                          }).toList(),
                        );
                      }).toList(),
                    );
                  }).toList(),
                ),
    );
  }
}
