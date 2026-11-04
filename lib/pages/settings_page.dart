import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_lucide/flutter_lucide.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../services/app_theme.dart';
import '../services/audio_preferences.dart';
import '../services/book_open_preferences.dart';
import '../services/library_store.dart';
import '../widgets/home/home_design.dart';
import '../widgets/settings/settings_widgets.dart';
import 'about_page.dart';
import 'api_service_page.dart';
import 'cached_books_page.dart';
import 'library_backup_page.dart';
import 'settings_category_page.dart';
import 'system_tts_settings_page.dart';
import 'text_replacement_page.dart';

/// Settings home: a list of category entries (PiliPlus multi-level
/// navigation). Each category opens its own CommonSetting-style page.
class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    // 开关读的是 ValueNotifier：先把持久化的值读出来，读到后自动重绘。
    unawaited(BookOpenPreferences.instance.load());
    final categories = <SettingsCategory>[
      SettingsCategory(
        icon: LucideIcons.palette,
        title: '外观',
        subtitle: '主题模式、OLED 纯黑',
        // 用 builder 而不是固定 items：切到深色 / 切回浅色后，分类页重建
        // 时会重新判定，OLED 行不必退出重进就能出现或消失。
        itemsBuilder: (context) => [
          _themeModeItem(),
          if (isDarkAppearance(
            systemBrightness: MediaQuery.platformBrightnessOf(context),
          ))
            _oledModeItem(),
        ],
      ),
      SettingsCategory(
        icon: LucideIcons.book_open,
        title: '阅读',
        subtitle: '阅读入口、正文替换',
        items: [
          _shelfDirectReaderItem(),
          _discoverDirectReaderItem(),
          SettingsItem(
            icon: LucideIcons.replace,
            title: '正文替换',
            subtitle: '自定义普通文本或正则替换规则',
            onTap: (context, _) => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const TextReplacementPage()),
            ),
          ),
        ],
      ),
      SettingsCategory(
        icon: LucideIcons.headphones,
        title: '听书',
        subtitle: '后台播放、通知栏控制与系统朗读',
        items: [
          _backgroundAudioItem(),
          _audioRateEngineItem(),
          SettingsItem(
            icon: LucideIcons.mic,
            title: '系统朗读',
            subtitle: '选择 TTS 引擎与语速，支持本地书',
            onTap: (context, setState) => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const SystemTtsSettingsPage()),
            ),
          ),
        ],
      ),
      SettingsCategory(
        icon: LucideIcons.database,
        title: '数据',
        subtitle: '备份与恢复、缓存、阅读与播放历史',
        items: [
          SettingsItem(
            icon: LucideIcons.database,
            title: '备份与导入',
            subtitle: '本机备份、恢复与番茄书架导入',
            onTap: (context, setState) => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const LibraryBackupPage()),
            ),
          ),
          SettingsItem(
            icon: LucideIcons.bookmark,
            title: '小说离线缓存',
            subtitle: '离线续读、查看占用和清理缓存',
            onTap: (context, setState) => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const CachedBooksPage()),
            ),
          ),
          _clearHistItem(),
        ],
      ),
      SettingsCategory(
        icon: LucideIcons.cpu,
        title: '服务',
        subtitle: '本地接口地址与可用端点',
        pageBuilder: (_) => const ApiServicePage(),
        items: const [],
      ),
      SettingsCategory(
        icon: LucideIcons.info,
        title: '关于',
        subtitle: '版本与应用信息',
        pageBuilder: (_) => const AboutPage(),
        items: const [],
      ),
    ];

    return SettingsScaffold(
      title: '设置',
      body: ListView(
              children: [
                SettingsGroupCard(
                  children: [
                    for (final cat in categories)
                      SettingsRow(
                        icon: cat.icon,
                        iconTile: true,
                        title: cat.title,
                        subtitle: cat.subtitle,
                        onTap: () => _openCategory(context, cat),
                      ),
                  ],
                ),
              ],
            ),
    );
  }

  static void _openCategory(BuildContext context, SettingsCategory cat) {
    final pageBuilder = cat.pageBuilder;
    Navigator.push(
      context,
      MaterialPageRoute(
        builder:
            pageBuilder ??
            (_) => SettingsCategoryPage(
              title: cat.title,
              items: cat.items,
              itemsBuilder: cat.itemsBuilder,
            ),
      ),
    );
  }

  // ── 外观 ──────────────────────────────────────────────────────────────

  /// 主题模式（参考 legado 的 themeMode）：跟随系统 / 浅色 / 深色 / 墨水屏
  /// 四选一，墨水屏既然也是一种外观就并进这个入口，不再单列一个开关。
  SettingsItem _themeModeItem() => SettingsItem(
    icon: LucideIcons.sun_moon,
    title: '主题模式',
    trailingBuilder: (context) => ListenableBuilder(
      listenable: Listenable.merge([themeModeNotifier, einkModeNotifier]),
      builder: (context, _) {
        final palette = HomePalette.of(context);
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              _appearance.label,
              style: TextStyle(fontSize: 13, color: palette.muted),
            ),
            const SizedBox(width: 4),
            Icon(LucideIcons.chevron_right, size: 18, color: palette.muted),
          ],
        );
      },
    ),
    onTap: (context, setState) => _showThemeModeDialog(context),
  );

  /// 当前外观：墨水屏优先级最高（深色反白在墨水屏上残影最重），
  /// 没有开墨水屏时再按深色模式取值。
  _AppearanceChoice get _appearance {
    if (einkModeNotifier.value) return _AppearanceChoice.eink;
    switch (themeModeNotifier.value) {
      case ThemeMode.light:
        return _AppearanceChoice.light;
      case ThemeMode.dark:
        return _AppearanceChoice.dark;
      case ThemeMode.system:
        return _AppearanceChoice.system;
    }
  }

  Future<void> _showThemeModeDialog(BuildContext context) async {
    final selected = await showDialog<_AppearanceChoice>(
      context: context,
      builder: (c) => SimpleDialog(
        title: const Text('主题模式'),
        children: [
          RadioGroup<_AppearanceChoice>(
            groupValue: _appearance,
            onChanged: (v) {
              if (v != null) Navigator.pop(c, v);
            },
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (final choice in _AppearanceChoice.values)
                  RadioListTile<_AppearanceChoice>(
                    value: choice,
                    title: Text(choice.label),
                    subtitle: choice == _AppearanceChoice.eink
                        ? const Text('纯黑白、无毛玻璃、无过渡动画，适合墨水屏设备')
                        : null,
                  ),
              ],
            ),
          ),
        ],
      ),
    );
    if (selected == null || !context.mounted) return;
    await _setAppearance(context, selected);
  }

  /// 主题模式落盘：浅色 / 深色 / 跟随系统写 theme_mode，墨水屏另存
  /// eink_mode。选墨水屏时保留原先的浅色 / 深色值，退出墨水屏能回到它。
  Future<void> _setAppearance(
    BuildContext context,
    _AppearanceChoice choice,
  ) async {
    final eink = choice == _AppearanceChoice.eink;
    final mode = switch (choice) {
      _AppearanceChoice.system => ThemeMode.system,
      _AppearanceChoice.light => ThemeMode.light,
      _AppearanceChoice.dark => ThemeMode.dark,
      _AppearanceChoice.eink => themeModeNotifier.value,
    };
    einkModeNotifier.value = eink;
    themeModeNotifier.value = mode;
    // 墨水屏恒为浅色外观，OLED 纯黑无从生效：一并关掉，否则以后切回深色
    // 会被上一次的纯黑默默接管。
    if (eink) oledModeNotifier.value = false;
    try {
      final sp = await SharedPreferences.getInstance();
      final modeSaved = await sp.setString(themeModeKey, mode.name);
      final einkSaved = await sp.setBool(einkModeKey, eink);
      final oledSaved = eink ? await sp.setBool(oledModeKey, false) : true;
      if (!modeSaved || !einkSaved || !oledSaved) {
        throw StateError('Appearance preference was not saved');
      }
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('外观已更新，但未能保存')));
      }
    }
  }

  /// OLED 纯黑：画布纯黑省电，卡片 / 弹层保持近黑，靠描边撑住层次。
  /// 只在深色外观下显示（浅色 / 墨水屏没有可省的黑）。
  SettingsItem _oledModeItem() => SettingsItem(
    icon: LucideIcons.smartphone,
    title: 'OLED 纯黑',
    subtitle: '大面积纯黑省电，仅在深色外观下生效',
    trailingBuilder: (context) => ValueListenableBuilder<bool>(
      valueListenable: oledModeNotifier,
      builder: (context, value, _) => SettingsSwitch(
        value: value,
        semanticLabel: 'OLED 纯黑',
        onChanged: (v) => unawaited(_setOledMode(context, v)),
      ),
    ),
    onTap: (context, setState) {
      unawaited(_setOledMode(context, !oledModeNotifier.value));
    },
  );

  Future<void> _setOledMode(BuildContext context, bool value) async {
    oledModeNotifier.value = value;
    try {
      final sp = await SharedPreferences.getInstance();
      if (!await sp.setBool(oledModeKey, value)) {
        throw StateError('OLED preference was not saved');
      }
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('OLED 纯黑已更新，但未能保存')));
      }
    }
  }


  // ── 阅读 ──────────────────────────────────────────────────────────────

  /// issue #19 第 3 条：点书跳过详情页，直接按上次进度进阅读器（小说/漫画）。

  /// 默认关＝现有行为（先进详情页）。听书/短剧与加载失败时仍停留在详情页。
  ///
  /// 书架的「我的书」和发现入口分成两条（issue #103）：书架里的书点开多半是
  /// 接着读，首页/搜索里的书往往还要先看简介，一条全局开关会让两边互相干扰。
  SettingsItem _shelfDirectReaderItem() => SettingsItem(
    icon: LucideIcons.book_open,
    title: '书架点书直接进入阅读',
    subtitle: '在书架、阅读历史里点书时跳过详情页，接着上次进度读',
    trailingBuilder: (context) => ValueListenableBuilder<bool>(
      valueListenable: BookOpenPreferences.instance.shelfDirectReader,
      builder: (context, value, _) => SettingsSwitch(
        value: value,
        semanticLabel: '书架点书直接进入阅读',
        onChanged: (v) =>
            unawaited(BookOpenPreferences.instance.setShelfDirectReader(v)),
      ),
    ),
    onTap: (context, setState) {
      final current = BookOpenPreferences.instance.shelfDirectReader.value;
      unawaited(BookOpenPreferences.instance.setShelfDirectReader(!current));
    },
  );

  SettingsItem _discoverDirectReaderItem() => SettingsItem(
    icon: LucideIcons.search,
    title: '首页与搜索点书直接进入阅读',
    subtitle: '在首页、搜索、榜单、作者页等入口点书时跳过详情页',
    trailingBuilder: (context) => ValueListenableBuilder<bool>(
      valueListenable: BookOpenPreferences.instance.discoverDirectReader,
      builder: (context, value, _) => SettingsSwitch(
        value: value,
        semanticLabel: '首页与搜索点书直接进入阅读',
        onChanged: (v) =>
            unawaited(BookOpenPreferences.instance.setDiscoverDirectReader(v)),
      ),
    ),
    onTap: (context, setState) {
      final current = BookOpenPreferences.instance.discoverDirectReader.value;
      unawaited(BookOpenPreferences.instance.setDiscoverDirectReader(!current));
    },
  );
  // ── 听书 ──────────────────────────────────────────────────────────────

  SettingsItem _backgroundAudioItem() => SettingsItem(
    icon: LucideIcons.radio,
    title: '后台听书与保活',
    subtitle: '切到后台或锁屏时继续播放，并在通知栏常驻控制卡片',
    trailingBuilder: (context) => ValueListenableBuilder<bool>(
      valueListenable: AudioPreferences.instance.backgroundPlayback,
      builder: (context, value, _) => SettingsSwitch(
        value: value,
        semanticLabel: '后台听书与保活',
        onChanged: (v) =>
            unawaited(AudioPreferences.instance.setBackgroundPlayback(v)),
      ),
    ),
    onTap: (context, setState) {
      final current = AudioPreferences.instance.backgroundPlayback.value;
      unawaited(AudioPreferences.instance.setBackgroundPlayback(!current));
    },
  );

  /// 听书倍速的变速实现（issue #129）：默认交给系统混音器，高倍速下明显
  /// 没那么「电音」；内建 Sonic 作为厂商实现异常时的回退档。
  ///
  /// 与「视频解码器」同理：audio sink 在 build 时定型，新建播放器才生效。
  SettingsItem _audioRateEngineItem() => SettingsItem(
    icon: LucideIcons.gauge,
    title: '听书变速引擎',
    subtitle: '高倍速发毛、像电音时可在两档间切换，重新进入听书页生效',
    trailingBuilder: (context) => ValueListenableBuilder<AudioRateEngine>(
      valueListenable: AudioPreferences.instance.rateEngine,
      builder: (context, value, _) {
        final palette = HomePalette.of(context);
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              value.label,
              style: TextStyle(fontSize: 13, color: palette.muted),
            ),
            const SizedBox(width: 4),
            Icon(LucideIcons.chevron_right, size: 18, color: palette.muted),
          ],
        );
      },
    ),
    onTap: (context, setState) => _showAudioRateEngineDialog(context, setState),
  );

  Future<void> _showAudioRateEngineDialog(
    BuildContext context,
    StateSetter refresh,
  ) async {
    final current = AudioPreferences.instance.rateEngine.value;
    final selected = await showDialog<AudioRateEngine>(
      context: context,
      builder: (c) => SimpleDialog(
        title: const Text('听书变速引擎'),
        children: [
          RadioGroup<AudioRateEngine>(
            groupValue: current,
            onChanged: (v) {
              if (v != null) Navigator.pop(c, v);
            },
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (final value in AudioRateEngine.values)
                  RadioListTile<AudioRateEngine>(
                    value: value,
                    title: Text(value.label),
                    subtitle: Text(switch (value) {
                      AudioRateEngine.wsola =>
                        '与番茄官方同一套算法（FFmpeg atempo），高倍速听感最接近原声',
                      AudioRateEngine.platform => '系统混音器变速，不依赖本应用的 native 库',
                      AudioRateEngine.builtIn =>
                        'Media3 内建 Sonic，最稳（默认，1.0.121 的行为）',
                    }),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
    if (selected == null || !context.mounted) return;
    await AudioPreferences.instance.setRateEngine(selected);
    refresh(() {});
  }


  // ── 数据 ──────────────────────────────────────────────────────────────

  SettingsItem _clearHistItem() => SettingsItem(
    icon: LucideIcons.trash,
    title: '清空历史',
    subtitle: '删除全部阅读/播放历史与累计时长',
    onTap: (context, setState) => _confirmClear(
      context,
      '清空历史',
      '确定清空全部阅读/播放历史与累计时长吗？',
      LibraryStore.instance.clearReadingData,
    ),
  );

  Future<void> _confirmClear(
    BuildContext context,
    String title,
    String message,
    Future<void> Function() action,
  ) async {
    final ok = await showSettingsConfirmDialog(
      context: context,
      title: title,
      message: message,
      confirmLabel: '清空',
      destructive: true,
    );
    if (!ok || !context.mounted) return;
    try {
      await action();
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('已清空')));
      }
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('清空失败，请重试')));
      }
    }
  }

  // ── 关于 ──────────────────────────────────────────────────────────────
  // The About category opens the About page directly via its pageBuilder;
  // no category-page items are needed.
}

/// 主题模式的可选项：三种深浅色模式 + 墨水屏。墨水屏本不是 ThemeMode，
/// 但用户眼里它就是第四种外观，所以放进同一个选择器（同 legado）。
enum _AppearanceChoice {
  system('跟随系统'),
  light('浅色'),
  dark('深色'),
  eink('墨水屏');

  const _AppearanceChoice(this.label);

  final String label;
}
