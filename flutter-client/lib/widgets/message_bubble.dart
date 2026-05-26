import 'dart:async';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:video_player/video_player.dart';

import '../models/message.dart';

class MessageBubble extends StatelessWidget {
  const MessageBubble({
    super.key,
    required this.message,
    this.onLongPress,
    this.showSender = false,
    this.senderName,
    this.authToken,
    this.currentUserId,
  });

  final Message message;
  final VoidCallback? onLongPress;
  final bool showSender;
  final String? senderName;
  final String? authToken;
  final String? currentUserId;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    if (message.direction == 'system') {
      return _buildSystemMessage(context);
    }

    // Determine if current user is @mentioned
    final isMentioned = currentUserId != null &&
        currentUserId!.isNotEmpty &&
        message.content.contains('@$currentUserId');

    Widget bubble = Align(
      alignment: message.mine ? Alignment.centerRight : Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.76),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 5),
          child: GestureDetector(
            onLongPress: onLongPress,
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: isMentioned
                    ? const Color(0xFFFFF3CD)
                    : (message.mine ? colorScheme.primary : Colors.white),
                borderRadius: BorderRadius.circular(8),
                border: (message.mine && !isMentioned)
                    ? null
                    : Border.all(
                        color: isMentioned
                            ? const Color(0xFFFFC107)
                            : colorScheme.outlineVariant),
              ),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Sender name for group chats
                    if (showSender && senderName != null)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 4),
                        child: Text(
                          senderName!,
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: isMentioned
                                ? Colors.black87
                                : (message.mine
                                    ? colorScheme.onPrimary.withAlpha(200)
                                    : colorScheme.primary),
                          ),
                        ),
                      ),
                    // Message content based on type
                    if (message.recalled)
                      _buildRecalledText(context)
                    else
                      _buildMessageContent(context),
                    // Reaction
                    if (message.reaction != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Text(
                          message.reaction!,
                          style: TextStyle(
                            fontSize: 12,
                            color: isMentioned
                                ? Colors.black54
                                : (message.mine
                                    ? colorScheme.onPrimary.withAlpha(180)
                                    : colorScheme.onSurfaceVariant),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );

    // Wrap with burn-after-read timer if needed
    if (message.expireAfterReadSeconds != null &&
        message.expireAfterReadSeconds! > 0) {
      bubble = _BurnAfterReadWrapper(
        durationSeconds: message.expireAfterReadSeconds!,
        isFromOther: !message.mine,
        child: bubble,
      );
    }

    return bubble;
  }

  Widget _buildSystemMessage(BuildContext context) {
    final text = message.content;

    Color bgColor;
    IconData icon;
    Color textColor = Colors.black87;

    if (text.contains('创建') || text.contains('created')) {
      bgColor = const Color(0xFFDCFCE7); // green bg
      icon = Icons.add_circle;
    } else if (text.contains('添加') || text.contains('added') || text.contains('邀请')) {
      bgColor = const Color(0xFFDBEAFE); // blue bg
      icon = Icons.person_add;
    } else if (text.contains('移除') || text.contains('removed') || text.contains('踢出')) {
      bgColor = const Color(0xFFFEE2E2); // red bg
      icon = Icons.person_remove;
    } else if (text.contains('改名') || text.contains('renamed')) {
      bgColor = const Color(0xFFFFEDD5); // orange bg
      icon = Icons.edit;
    } else if (text.contains('公告') || text.contains('announce')) {
      bgColor = const Color(0xFFF3E8FF); // purple bg
      icon = Icons.campaign;
    } else {
      bgColor = const Color(0xFFE2E8F0); // gray bg (default)
      icon = Icons.info_outline;
      textColor = (Theme.of(context).textTheme.bodySmall?.color) ?? Colors.black54;
    }

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Center(
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: bgColor,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(icon, size: 14, color: textColor),
                const SizedBox(width: 4),
                Flexible(
                  child: Text(
                    message.content,
                    style: TextStyle(
                      fontSize: Theme.of(context).textTheme.bodySmall?.fontSize ?? 12,
                      color: textColor,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildRecalledText(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Text(
      '消息已撤回',
      style: TextStyle(
        color: message.mine ? colorScheme.onPrimary : colorScheme.onSurface,
        height: 1.35,
        fontStyle: FontStyle.italic,
      ),
    );
  }

  Widget _buildMessageContent(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    switch (message.type) {
      case 'image':
        return _buildImageMessage(context, colorScheme);
      case 'file':
        return _buildFileMessage(context, colorScheme);
      case 'voice':
        return _buildVoiceMessage(context, colorScheme);
      case 'video':
        return _buildVideoMessage(context, colorScheme);
      default:
        return _buildTextMessage(context, colorScheme);
    }
  }

  Widget _buildTextMessage(BuildContext context, ColorScheme colorScheme) {
    final isMentioned = currentUserId != null &&
        currentUserId!.isNotEmpty &&
        message.content.contains('@$currentUserId');

    final defaultColor = isMentioned
        ? Colors.black87
        : (message.mine ? colorScheme.onPrimary : colorScheme.onSurface);

    final spans = _parseTextToSpans(message.content, defaultColor);

    // If no special spans were found, fall back to plain Text
    if (spans.length == 1 && spans.first is TextSpan && (spans.first as TextSpan).recognizer == null) {
      return Text(
        message.content,
        style: TextStyle(color: defaultColor, height: 1.35),
      );
    }

    return RichText(
      text: TextSpan(
        style: TextStyle(color: defaultColor, height: 1.35),
        children: spans,
      ),
    );
  }

  List<InlineSpan> _parseTextToSpans(String text, Color defaultColor) {
    final spans = <InlineSpan>[];
    final urlRegExp = RegExp(r'https?://[^\s]+');
    final phoneRegExp = RegExp(r'(?:\+86[-.\s]?)?1\d{2}[-.\s]?\d{4}[-.\s]?\d{4}');
    final mentionRegExp = RegExp(r'@\S+');

    int currentIndex = 0;

    while (currentIndex < text.length) {
      // Find matches starting at currentIndex; URLs take priority,
      // then phone numbers, then @mentions.
      final urlMatch = urlRegExp.matchAsPrefix(text, currentIndex);
      final phoneMatch = phoneRegExp.matchAsPrefix(text, currentIndex);
      final mentionMatch = mentionRegExp.matchAsPrefix(text, currentIndex);

      Match? bestMatch;
      String? matchType;

      if (urlMatch != null) {
        bestMatch = urlMatch;
        matchType = 'url';
      }
      if (phoneMatch != null &&
          (bestMatch == null || phoneMatch.end > bestMatch.end)) {
        bestMatch = phoneMatch;
        matchType = 'phone';
      }
      if (mentionMatch != null &&
          (bestMatch == null || mentionMatch.end > bestMatch.end)) {
        bestMatch = mentionMatch;
        matchType = 'mention';
      }

      if (bestMatch != null) {
        // Regular text before the match
        if (currentIndex < bestMatch.start) {
          spans.add(TextSpan(text: text.substring(currentIndex, bestMatch.start)));
        }

        final matchedText = bestMatch.group(0)!;

        if (matchType == 'url') {
          spans.add(TextSpan(
            text: matchedText,
            style: const TextStyle(
              color: Colors.blue,
              decoration: TextDecoration.underline,
            ),
            recognizer: TapGestureRecognizer()
              ..onTap = () {
                final uri = Uri.tryParse(matchedText);
                if (uri != null) {
                  launchUrl(uri, mode: LaunchMode.externalApplication);
                }
              },
          ));
        } else if (matchType == 'phone') {
          spans.add(TextSpan(
            text: matchedText,
            style: const TextStyle(
              color: Colors.blue,
              decoration: TextDecoration.underline,
            ),
            recognizer: TapGestureRecognizer()
              ..onTap = () {
                final telUri = Uri.tryParse('tel:$matchedText');
                if (telUri != null) {
                  launchUrl(telUri, mode: LaunchMode.externalApplication);
                }
              },
          ));
        } else {
          // @mention
          spans.add(TextSpan(
            text: matchedText,
            style: TextStyle(
              color: Colors.blue.shade700,
              fontWeight: FontWeight.w600,
            ),
          ));
        }

        currentIndex = bestMatch.end;
      } else {
        // No match at currentIndex — find the next potential start position
        final nextUrl = text.indexOf(RegExp(r'https?://'), currentIndex + 1);
        final nextDigit = text.indexOf(RegExp(r'[\+1]'), currentIndex + 1);
        final nextAt = text.indexOf('@', currentIndex + 1);

        final candidates = <int>[
          if (nextUrl >= 0) nextUrl,
          if (nextDigit >= 0) nextDigit,
          if (nextAt >= 0) nextAt,
        ];

        if (candidates.isEmpty) {
          spans.add(TextSpan(text: text.substring(currentIndex)));
          break;
        }

        final nextIdx = candidates.reduce((a, b) => a < b ? a : b);
        spans.add(TextSpan(text: text.substring(currentIndex, nextIdx)));
        currentIndex = nextIdx;
      }
    }

    return spans;
  }

  Widget _buildImageMessage(BuildContext context, ColorScheme colorScheme) {
    final url = message.fileUrl;
    if (url == null || url.isEmpty) {
      return _buildTextMessage(context, colorScheme);
    }

    return GestureDetector(
      onTap: () => _openImageViewer(context, url),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxHeight: 200, minWidth: 120),
          child: Image.network(
            url,
            headers: authToken == null || authToken!.isEmpty
                ? null
                : {'Authorization': 'Bearer $authToken'},
            fit: BoxFit.cover,
            loadingBuilder: (context, child, loadingProgress) {
              if (loadingProgress == null) return child;
              return SizedBox(
                height: 120,
                width: 160,
                child: Center(
                  child: CircularProgressIndicator(
                    value: loadingProgress.expectedTotalBytes != null
                        ? loadingProgress.cumulativeBytesLoaded /
                            loadingProgress.expectedTotalBytes!
                        : null,
                    strokeWidth: 2,
                    color: message.mine ? colorScheme.onPrimary : colorScheme.primary,
                  ),
                ),
              );
            },
            errorBuilder: (context, error, stackTrace) {
              return SizedBox(
                height: 80,
                width: 120,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.broken_image_outlined,
                      color: message.mine
                          ? colorScheme.onPrimary.withAlpha(180)
                          : colorScheme.onSurfaceVariant,
                      size: 32,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '图片加载失败',
                      style: TextStyle(
                        fontSize: 11,
                        color: message.mine
                            ? colorScheme.onPrimary.withAlpha(180)
                            : colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _buildFileMessage(BuildContext context, ColorScheme colorScheme) {
    final name = message.fileName ?? '未知文件';
    final size = message.fileSize;
    final ext = _getFileExtension(name);

    return InkWell(
      onTap: message.fileUrl == null ? null : () => _openFileUrl(context, message.fileUrl!),
      borderRadius: BorderRadius.circular(8),
      child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: _getFileColor(ext).withAlpha(30),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(
            _getFileIcon(ext),
            color: _getFileColor(ext),
            size: 24,
          ),
        ),
        const SizedBox(width: 10),
        Flexible(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                _truncateFileName(name, 20),
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: message.mine ? colorScheme.onPrimary : colorScheme.onSurface,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              if (size != null)
                Text(
                  _formatFileSize(size),
                  style: TextStyle(
                    fontSize: 12,
                    color: message.mine
                        ? colorScheme.onPrimary.withAlpha(180)
                        : colorScheme.onSurfaceVariant,
                  ),
                ),
            ],
          ),
        ),
      ],
      ),
    );
  }

  Widget _buildVoiceMessage(BuildContext context, ColorScheme colorScheme) {
    final url = message.fileUrl;
    if (url == null || url.isEmpty) {
      return _buildTextMessage(context, colorScheme);
    }
    return _VoicePlayerWidget(url: url, isMe: message.mine, colorScheme: colorScheme);
  }

  Widget _buildVideoMessage(BuildContext context, ColorScheme colorScheme) {
    final url = message.fileUrl;
    final thumbnailUrl = message.thumbnailUrl;

    return GestureDetector(
      onTap: () {
        if (url != null && url.isNotEmpty) {
          _openVideoPlayer(context, url);
        }
      },
      child: ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: Stack(
          alignment: Alignment.center,
          children: [
            if (thumbnailUrl != null && thumbnailUrl.isNotEmpty)
              ConstrainedBox(
                constraints: const BoxConstraints(maxHeight: 180, minWidth: 140),
                child: Image.network(
                  thumbnailUrl,
                  fit: BoxFit.cover,
                  errorBuilder: (context, error, stackTrace) {
                    return Container(
                      height: 120,
                      width: 160,
                      color: Colors.black12,
                      child: Icon(
                        Icons.videocam_outlined,
                        color: message.mine
                            ? colorScheme.onPrimary.withAlpha(180)
                            : colorScheme.onSurfaceVariant,
                        size: 40,
                      ),
                    );
                  },
                ),
              )
            else
              Container(
                height: 120,
                width: 160,
                color: Colors.black12,
                child: Icon(
                  Icons.videocam_outlined,
                  color: message.mine
                      ? colorScheme.onPrimary.withAlpha(180)
                      : colorScheme.onSurfaceVariant,
                  size: 40,
                ),
              ),
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: Colors.black.withAlpha(128),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.play_arrow, color: Colors.white, size: 32),
            ),
          ],
        ),
      ),
    );
  }

  void _openVideoPlayer(BuildContext context, String url) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => _VideoPlayerScreen(url: url),
      ),
    );
  }

  void _openImageViewer(BuildContext context, String url) {
    showDialog<void>(
      context: context,
      builder: (context) => Dialog(
        backgroundColor: Colors.black,
        insetPadding: EdgeInsets.zero,
        child: Stack(
          children: [
            InteractiveViewer(
              minScale: 0.5,
              maxScale: 4.0,
              child: Center(
                child: Image.network(
                  url,
                  fit: BoxFit.contain,
                  loadingBuilder: (context, child, loadingProgress) {
                    if (loadingProgress == null) return child;
                    return const Center(
                      child: CircularProgressIndicator(color: Colors.white),
                    );
                  },
                  errorBuilder: (context, error, stackTrace) {
                    return const Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.broken_image_outlined, color: Colors.white54, size: 48),
                          SizedBox(height: 8),
                          Text('图片加载失败', style: TextStyle(color: Colors.white54)),
                        ],
                      ),
                    );
                  },
                ),
              ),
            ),
            Positioned(
              top: MediaQuery.of(context).padding.top + 8,
              right: 8,
              child: IconButton(
                icon: const Icon(Icons.close, color: Colors.white, size: 28),
                onPressed: () => Navigator.of(context).pop(),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _openFileUrl(BuildContext context, String url) async {
    final uri = Uri.tryParse(url);
    if (uri == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('文件地址无效')));
      return;
    }
    final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!opened && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('无法打开文件')));
    }
  }

  // File helper methods

  String _getFileExtension(String fileName) {
    final dotIndex = fileName.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == fileName.length - 1) return '';
    return fileName.substring(dotIndex + 1).toLowerCase();
  }

  IconData _getFileIcon(String ext) {
    switch (ext) {
      case 'pdf':
        return Icons.picture_as_pdf;
      case 'doc':
      case 'docx':
        return Icons.description;
      case 'xls':
      case 'xlsx':
        return Icons.table_chart;
      case 'ppt':
      case 'pptx':
        return Icons.slideshow;
      case 'zip':
      case 'rar':
      case '7z':
      case 'tar':
      case 'gz':
        return Icons.archive;
      case 'mp3':
      case 'wav':
      case 'aac':
      case 'ogg':
        return Icons.audiotrack;
      case 'mp4':
      case 'avi':
      case 'mov':
      case 'mkv':
        return Icons.videocam;
      case 'txt':
      case 'log':
        return Icons.text_snippet;
      case 'jpg':
      case 'jpeg':
      case 'png':
      case 'gif':
      case 'bmp':
      case 'webp':
        return Icons.image;
      default:
        return Icons.insert_drive_file;
    }
  }

  Color _getFileColor(String ext) {
    switch (ext) {
      case 'pdf':
        return const Color(0xFFDC2626); // red
      case 'doc':
      case 'docx':
        return const Color(0xFF2563EB); // blue
      case 'xls':
      case 'xlsx':
        return const Color(0xFF16A34A); // green
      case 'ppt':
      case 'pptx':
        return const Color(0xFFEA580C); // orange
      case 'zip':
      case 'rar':
      case '7z':
      case 'tar':
      case 'gz':
        return const Color(0xFF9333EA); // purple
      default:
        return const Color(0xFF64748B); // gray
    }
  }

  String _truncateFileName(String name, int maxLength) {
    if (name.length <= maxLength) return name;
    final dotIndex = name.lastIndexOf('.');
    if (dotIndex < 0) {
      return '${name.substring(0, maxLength - 3)}...';
    }
    final ext = name.substring(dotIndex);
    final baseName = name.substring(0, dotIndex);
    final availableLength = maxLength - ext.length - 3;
    if (availableLength <= 0) {
      return '${name.substring(0, maxLength - 3)}...';
    }
    return '${baseName.substring(0, availableLength)}...$ext';
  }

  String _formatFileSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
  }
}

class _VoicePlayerWidget extends StatefulWidget {
  const _VoicePlayerWidget({required this.url, required this.isMe, required this.colorScheme});

  final String url;
  final bool isMe;
  final ColorScheme colorScheme;

  @override
  State<_VoicePlayerWidget> createState() => _VoicePlayerWidgetState();
}

class _VoicePlayerWidgetState extends State<_VoicePlayerWidget> {
  final _player = AudioPlayer();
  bool _playing = false;

  @override
  void initState() {
    super.initState();
    _player.onPlayerComplete.listen((_) {
      if (mounted) setState(() => _playing = false);
    });
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  Future<void> _togglePlay() async {
    if (_playing) {
      await _player.pause();
      setState(() => _playing = false);
    } else {
      await _player.play(UrlSource(widget.url));
      setState(() => _playing = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final color = widget.isMe ? widget.colorScheme.onPrimary : widget.colorScheme.primary;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        GestureDetector(
          onTap: _togglePlay,
          child: Icon(
            _playing ? Icons.pause_circle_filled : Icons.play_circle_filled,
            color: color,
            size: 32,
          ),
        ),
        const SizedBox(width: 8),
        SizedBox(
          width: 60,
          height: 24,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: List.generate(5, (index) {
              final heights = [8.0, 16.0, 20.0, 14.0, 10.0];
              return Container(
                width: 3,
                height: heights[index],
                decoration: BoxDecoration(
                  color: color.withAlpha(_playing ? 200 : 100),
                  borderRadius: BorderRadius.circular(2),
                ),
              );
            }),
          ),
        ),
      ],
    );
  }
}

class _VideoPlayerScreen extends StatefulWidget {
  const _VideoPlayerScreen({required this.url});

  final String url;

  @override
  State<_VideoPlayerScreen> createState() => _VideoPlayerScreenState();
}

class _VideoPlayerScreenState extends State<_VideoPlayerScreen> {
  VideoPlayerController? _controller;
  bool _initialized = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _initPlayer();
  }

  Future<void> _initPlayer() async {
    try {
      final controller = VideoPlayerController.networkUrl(Uri.parse(widget.url));
      _controller = controller;
      await controller.initialize();
      if (mounted) {
        setState(() => _initialized = true);
        controller.play();
      }
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        foregroundColor: Colors.white,
        title: const Text('视频播放'),
      ),
      body: Center(
        child: _error != null
            ? Text('播放失败: $_error', style: const TextStyle(color: Colors.white))
            : _initialized && _controller != null
                ? AspectRatio(
                    aspectRatio: _controller!.value.aspectRatio,
                    child: Stack(
                      alignment: Alignment.bottomCenter,
                      children: [
                        VideoPlayer(_controller!),
                        VideoProgressIndicator(_controller!, allowScrubbing: true),
                        GestureDetector(
                          onTap: () {
                            if (_controller!.value.isPlaying) {
                              _controller!.pause();
                            } else {
                              _controller!.play();
                            }
                            setState(() {});
                          },
                          child: Center(
                            child: Icon(
                              _controller!.value.isPlaying ? Icons.pause_circle : Icons.play_circle,
                              color: Colors.white.withAlpha(180),
                              size: 56,
                            ),
                          ),
                        ),
                      ],
                    ),
                  )
                : const CircularProgressIndicator(color: Colors.white),
      ),
    );
  }
}

class _BurnAfterReadWrapper extends StatefulWidget {
  const _BurnAfterReadWrapper({
    required this.child,
    required this.durationSeconds,
    required this.isFromOther,
  });

  final Widget child;
  final int durationSeconds;
  final bool isFromOther;

  @override
  State<_BurnAfterReadWrapper> createState() => _BurnAfterReadWrapperState();
}

class _BurnAfterReadWrapperState extends State<_BurnAfterReadWrapper> {
  late int _remaining;
  Timer? _timer;
  bool _expired = false;

  @override
  void initState() {
    super.initState();
    _remaining = widget.durationSeconds;
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() {
        _remaining--;
        if (_remaining <= 0) {
          _timer?.cancel();
          _expired = true;
        }
      });
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedOpacity(
      opacity: _expired ? 0.0 : 1.0,
      duration: const Duration(milliseconds: 500),
      child: Stack(
        children: [
          widget.child,
          if (widget.isFromOther)
            Positioned(
              right: 4,
              top: 4,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: Colors.black.withAlpha(128),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.local_fire_department,
                        color: Colors.orange, size: 12),
                    const SizedBox(width: 2),
                    Text(
                      '${_remaining}s',
                      style: const TextStyle(
                          color: Colors.white, fontSize: 11),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
