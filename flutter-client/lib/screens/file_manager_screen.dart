import 'package:flutter/material.dart';

import '../services/api_service.dart';

class FileManagerScreen extends StatefulWidget {
  const FileManagerScreen({
    super.key,
    required this.apiService,
    required this.currentUserId,
    required this.onBack,
  });

  final ApiService apiService;
  final String currentUserId;
  final VoidCallback onBack;

  @override
  State<FileManagerScreen> createState() => _FileManagerScreenState();
}

class _FileManagerScreenState extends State<FileManagerScreen> with SingleTickerProviderStateMixin {
  late final TabController _tabController;
  List<Map<String, Object?>> _files = [];
  bool _loading = true;
  String? _error;

  static const _tabs = [
    ('全部', null),
    ('图片', 'image'),
    ('文档', 'document'),
    ('视频', 'video'),
    ('音频', 'audio'),
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: _tabs.length, vsync: this);
    _tabController.addListener(() {
      if (!_tabController.indexIsChanging) _loadFiles();
    });
    _loadFiles();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadFiles() async {
    setState(() => _loading = true);
    try {
      final data = await widget.apiService.get('/api/files?userId=${Uri.encodeQueryComponent(widget.currentUserId)}&limit=100');
      var items = _dataList(data);
      final filter = _tabs[_tabController.index].$2;
      if (filter != null) {
        items = items.where((f) {
          final ct = f['content_type']?.toString() ?? '';
          return ct.startsWith('$filter/');
        }).toList();
      }
      if (mounted) {
        setState(() {
          _files = items;
          _loading = false;
          _error = null;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = e.toString();
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

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: widget.onBack),
        title: const Text('文件管理'),
        bottom: TabBar(
          controller: _tabController,
          isScrollable: true,
          tabAlignment: TabAlignment.start,
          tabs: _tabs.map((t) => Tab(text: t.$1)).toList(),
        ),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text('加载失败: $_error', style: TextStyle(color: colorScheme.error)))
              : _files.isEmpty
                  ? const Center(child: Text('暂无文件'))
                  : ListView.separated(
                      itemCount: _files.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, index) {
                        final file = _files[index];
                        final name = file['original_name']?.toString() ?? '未知文件';
                        final size = int.tryParse(file['size_bytes']?.toString() ?? '') ?? 0;
                        final id = file['id']?.toString() ?? '';
                        final previewUrl = '${widget.apiService.baseUrl}/api/files/$id/preview';
                        return ListTile(
                          leading: Icon(_iconForType(file['content_type']?.toString()), color: colorScheme.primary),
                          title: Text(name, maxLines: 1, overflow: TextOverflow.ellipsis),
                          subtitle: Text(_formatSize(size)),
                          trailing: IconButton(
                            icon: const Icon(Icons.download),
                            onPressed: () => _openPreview(previewUrl),
                          ),
                        );
                      },
                    ),
    );
  }

  IconData _iconForType(String? contentType) {
    if (contentType == null) return Icons.insert_drive_file;
    if (contentType.startsWith('image/')) return Icons.image;
    if (contentType.startsWith('video/')) return Icons.videocam;
    if (contentType.startsWith('audio/')) return Icons.audiotrack;
    if (contentType.contains('pdf')) return Icons.picture_as_pdf;
    return Icons.insert_drive_file;
  }

  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1048576) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1073741824) return '${(bytes / 1048576).toStringAsFixed(1)} MB';
    return '${(bytes / 1073741824).toStringAsFixed(1)} GB';
  }

  void _openPreview(String url) async {
    final uri = Uri.tryParse(url);
    if (uri == null) return;
    await Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => Scaffold(
        appBar: AppBar(title: const Text('预览')),
        body: Center(child: Image.network(url, errorBuilder: (_, __, ___) => const Text('预览不可用'))),
      ),
    ));
  }
}
