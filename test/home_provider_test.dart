import 'dart:async';
import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fqapp/models/home_cell.dart';
import 'package:fqapp/models/media_item.dart';
import 'package:fqapp/pages/home_provider.dart';
import 'package:fqapp/services/api_client.dart';
import 'package:fqapp/services/home_feed_cache.dart';
import 'package:hive_ce_flutter/hive_ce_flutter.dart';

void main() {
  setUpAll(() async {
    final directory = await Directory.systemTemp.createTemp(
      'fqapp-home-provider-test-',
    );
    Hive.init(directory.path);
  });

  tearDown(() async {
    await Hive.close();
    HomeFeedCache.hiveReady = false;
  });

  test(
    'an initial request can finish after its provider is disposed',
    () async {
      final response = Completer<HomepagePage>();
      final provider = NotifierProvider<HomeNotifier, HomeState>(
        () => HomeNotifier(
          homepageLoader:
              ({int tabType = 2, int offset = 0, String? sessionId}) =>
                  response.future,
          searchLoader: (query, {int page = 1}) async => const [],
        ),
      );
      final container = ProviderContainer();
      final request = container.read(provider.notifier).load();
      container.dispose();
      response.complete(
        HomepagePage(items: [_item('book')], nextOffset: null, sessionId: null),
      );
      await expectLater(request, completes);
    },
  );

  test('search fallback does not relabel other kinds as novels', () async {
    final provider = NotifierProvider<HomeNotifier, HomeState>(
      () => HomeNotifier(
        homepageLoader:
            ({int tabType = 2, int offset = 0, String? sessionId}) async =>
                throw StateError('recommendations unavailable'),
        searchLoader: (query, {int page = 1}) async => [
          SearchTab(title: '书籍', items: [_item('novel')]),
          SearchTab(
            title: '短剧',
            items: [_item('drama', kind: 'video')],
          ),
        ],
      ),
    );
    final container = ProviderContainer();
    addTearDown(container.dispose);
    await container.read(provider.notifier).load();
    await _flushMicrotasks();
    expect(container.read(provider).items.map((item) => item.id), ['novel']);
  });

  test('推荐流可用时首页只显示它自己的区块，不再混排其他来源', () async {
    // 官方书城 tab 只渲染自己的 block，不把短剧/漫画/听书交错进来。
    var searchCalls = 0;
    final provider = NotifierProvider<HomeNotifier, HomeState>(
      () => HomeNotifier(
        homepageLoader:
            ({int tabType = 2, int offset = 0, String? sessionId}) async =>
                HomepagePage(
                  items: [_item('rec-1'), _item('rec-2')],
                  cells: const [
                    HomeCell(
                      showType: 384,
                      name: '猜你喜欢',
                      id: '',
                      style: {},
                      items: [],
                      children: [],
                    ),
                  ],
                  nextOffset: null,
                  sessionId: null,
                ),
        searchLoader: (query, {int page = 1}) async {
          searchCalls++;
          return [
            SearchTab(title: '综合', items: [_item('search-$query')]),
          ];
        },
      ),
    );
    final container = ProviderContainer();
    addTearDown(container.dispose);
    await container.read(provider.notifier).load();
    final state = container.read(provider);

    // 只有推荐流的卡片，短剧/漫画/听书都不该被拉进来。
    expect(state.items.map((i) => i.id), ['rec-1', 'rec-2']);
    expect(state.cells, hasLength(1));
    expect(state.cells.single.name, '猜你喜欢');
    expect(searchCalls, 0, reason: '不再为混排去搜其他来源');
  });

  test('快照带区块时首帧即区块版，冷启动不再闪旧版网格', () async {
    HomeFeedCache.hiveReady = true;
    final cache = HomeFeedCache();
    await cache.save(
      0,
      [_item('cached-1')],
      hasMore: true,
      cells: const [
        HomeCell(
          showType: 511,
          name: '排行榜',
          id: '',
          style: {},
          items: [],
          children: [],
        ),
      ],
    );

    final releaseNetwork = Completer<void>();
    final provider = NotifierProvider<HomeNotifier, HomeState>(
      () => HomeNotifier(
        feedCache: cache,
        homepageLoader:
            ({int tabType = 2, int offset = 0, String? sessionId}) async {
              await releaseNetwork.future;
              return HomepagePage(
                items: [_item('fresh-1')],
                cells: const [
                  HomeCell(
                    showType: 384,
                    name: '猜你喜欢',
                    id: '',
                    style: {},
                    items: [],
                    children: [],
                  ),
                ],
                nextOffset: null,
                sessionId: null,
              );
            },
        searchLoader: (query, {int page = 1}) async => [],
      ),
    );
    final container = ProviderContainer();
    addTearDown(container.dispose);
    final loading = container.read(provider.notifier).load();

    // 首帧即区块版：网络还没回答，state.cells 就已经非空 —— home_page 的
    // 区块管线不再退回旧版聚光灯+平铺网格，冷启动不会先闪旧版首页。
    expect(container.read(provider).cells.map((cell) => cell.name), ['排行榜']);
    expect(container.read(provider).items.map((item) => item.id), ['cached-1']);

    releaseNetwork.complete();
    await loading;
    // 网络整页替换时 cells 照常跟着换（stale-while-revalidate 语义）。
    expect(container.read(provider).cells.map((cell) => cell.name), ['猜你喜欢']);
    expect(container.read(provider).items.map((item) => item.id), ['fresh-1']);
    await cache.close();
  });

  test('翻页失败置 error 而不是只置 hasMore（footer 重试的判定输入）', () async {
    // P1 回归：曾只置 hasMore=false，流尾会谎报「已显示全部内容」。
    // 页面 footer 的重试分支判定是 error != null && items.isNotEmpty。
    var page = 0;
    final provider = NotifierProvider<HomeNotifier, HomeState>(
      () => HomeNotifier(
        homepageLoader:
            ({int tabType = 2, int offset = 0, String? sessionId}) async {
              page += 1;
              if (page == 1) {
                return HomepagePage(
                  items: [_item('first-1')],
                  cells: const [
                    HomeCell(
                      showType: 384,
                      name: '',
                      id: '',
                      style: {},
                      items: [],
                      children: [
                        HomeCell(
                          showType: 385,
                          name: '',
                          id: '',
                          style: {},
                          items: [],
                          children: [],
                        ),
                      ],
                    ),
                  ],
                  nextOffset: 10,
                  sessionId: null,
                );
              }
              throw StateError('翻页失败');
            },
        searchLoader: (query, {int page = 1}) async => [],
      ),
    );
    final container = ProviderContainer();
    addTearDown(container.dispose);
    await container.read(provider.notifier).load();

    await container.read(provider.notifier).loadMore();

    final state = container.read(provider);
    expect(state.error, isNotNull);
    expect(state.hasMore, isFalse);
    // 已有内容保留（错误页不清空列表）。
    expect(state.items.map((i) => i.id), ['first-1']);
  });

  test('追加页的书拼进流区块，网格真的无限', () async {
    // 区块管线只渲染 cells；分页响应不带区块，若新书只进 items，网格永远
    // 停在首页那批卡。追加页必须合入 385 流区块（cellsForView）。
    var page = 0;
    final provider = NotifierProvider<HomeNotifier, HomeState>(
      () => HomeNotifier(
        homepageLoader:
            ({int tabType = 2, int offset = 0, String? sessionId}) async {
              page += 1;
              if (page == 1) {
                return HomepagePage(
                  items: [_item('first-1'), _item('first-2')],
                  cells: const [
                    HomeCell(
                      showType: 384,
                      name: '猜你喜欢',
                      id: '',
                      style: {},
                      items: [],
                      children: [
                        HomeCell(
                          showType: 385,
                          name: '',
                          id: '',
                          style: {},
                          items: [],
                          children: [],
                        ),
                      ],
                    ),
                  ],
                  nextOffset: 10,
                  sessionId: null,
                );
              }
              // 续页：只有 items，没有 cells。
              return HomepagePage(
                items: [_item('next-1'), _item('next-2')],
                nextOffset: null,
                sessionId: null,
              );
            },
        searchLoader: (query, {int page = 1}) async => [],
      ),
    );
    final container = ProviderContainer();
    addTearDown(container.dispose);
    await container.read(provider.notifier).load();

    final firstCells = container.read(provider).cells;
    expect(firstCells.single.showType, 384);
    expect(firstCells.single.children.single.items.map((i) => i.id), isEmpty);

    await container.read(provider.notifier).loadMore();

    // 追加页的书出现在流区块的渲染列表里。
    final merged = container.read(provider).cells;
    expect(merged.single.children.single.items.map((i) => i.id), [
      'next-1',
      'next-2',
    ]);
    // items 也照常追加（旧管线/去重语义不变）。
    expect(container.read(provider).items.map((i) => i.id), [
      'first-1',
      'first-2',
      'next-1',
      'next-2',
    ]);
  });

  test('the combined feed keeps novels when recommendations fail', () async {
    final queries = <String>[];
    final provider = NotifierProvider<HomeNotifier, HomeState>(
      () => HomeNotifier(
        homepageLoader:
            ({int tabType = 2, int offset = 0, String? sessionId}) async =>
                throw StateError('old backend'),
        searchLoader: (query, {int page = 1}) async {
          queries.add('$query:$page');
          return [
            SearchTab(
              title: '综合',
              items: query == '小说' ? [_item('novel-$page')] : const [],
            ),
          ];
        },
      ),
    );
    final container = ProviderContainer();
    addTearDown(container.dispose);
    final notifier = container.read(provider.notifier);
    await notifier.load();
    expect(container.read(provider).items.map((item) => item.id), ['novel-1']);
    await notifier.loadMore();
    expect(container.read(provider).items.map((item) => item.id), [
      'novel-1',
      'novel-2',
    ]);
    expect(queries, containsAll(['小说:1', '小说:2']));
  });

  test('首页首屏兜底：小说流与搜索都空了也混入听书', () async {
    final provider = NotifierProvider<HomeNotifier, HomeState>(
      () => HomeNotifier(
        homepageLoader:
            ({int tabType = 2, int offset = 0, String? sessionId}) async =>
                throw StateError('recommendations unavailable'),
        searchLoader: (query, {int page = 1}) async => query == '听书'
            ? [
                SearchTab(
                  title: '听书',
                  items: [_item('a', kind: 'audio')],
                ),
              ]
            : const [],
      ),
    );
    final container = ProviderContainer();
    addTearDown(container.dispose);
    await container.read(provider.notifier).load();
    expect(container.read(provider).items.map((item) => item.id), ['a']);
  });

  test('a late response cannot overwrite or populate another tab', () async {
    final requests = <int, List<Completer<HomepagePage>>>{};

    Future<HomepagePage> loadHomepage({
      int tabType = 2,
      int offset = 0,
      String? sessionId,
    }) {
      final completer = Completer<HomepagePage>();
      requests.putIfAbsent(tabType, () => []).add(completer);
      return completer.future;
    }

    Future<List<SearchTab>> loadSearch(String query, {int page = 1}) async =>
        const [];

    final provider = NotifierProvider<HomeNotifier, HomeState>(
      () =>
          HomeNotifier(homepageLoader: loadHomepage, searchLoader: loadSearch),
    );
    final container = ProviderContainer();
    addTearDown(container.dispose);
    final notifier = container.read(provider.notifier);

    unawaited(notifier.load()); // 小说（默认下标 0）, request still pending.
    expect(requests[2], hasLength(1));
    notifier.selectTab(1); // 听书 supersedes it.
    expect(requests[5], hasLength(1));

    requests[5]!.single.complete(
      HomepagePage(
        items: [_item('audio', kind: 'audio')],
        nextOffset: null,
        sessionId: null,
      ),
    );
    await _flushMicrotasks();
    expect(container.read(provider).tabIndex, 1);
    expect(container.read(provider).items.single.id, 'audio');
    expect(container.read(provider).items.single.kind, 'audio');

    // Completing the older novel request must not change the selected feed.
    requests[2]!.single.complete(
      HomepagePage(
        items: [_item('stale-book')],
        nextOffset: null,
        sessionId: null,
      ),
    );
    await _flushMicrotasks();
    expect(container.read(provider).tabIndex, 1);
    expect(container.read(provider).items.single.id, 'audio');

    // The canceled novel request must not make an empty cache look loaded.
    notifier.selectTab(0);
    expect(requests[2], hasLength(2));
    requests[2]!.last.complete(
      HomepagePage(
        items: [_item('fresh-book')],
        nextOffset: null,
        sessionId: null,
      ),
    );
    await _flushMicrotasks();
    expect(container.read(provider).items.single.id, 'fresh-book');
    expect(container.read(provider).items.single.kind, 'book');
  });

  group('server tab strip drives the tab identity', () {
    const serverTabs = <HomeTabItem>[
      HomeTabItem(type: 2, title: '推荐'),
      HomeTabItem(type: 25, title: '小说'),
      HomeTabItem(type: 11, title: '经典'),
      HomeTabItem(type: 12, title: '知识'),
      HomeTabItem(type: 5, title: '听书'),
      HomeTabItem(type: 8, title: '看剧'),
      HomeTabItem(type: 16, title: '视频'),
      HomeTabItem(type: 103, title: '短篇'),
      HomeTabItem(type: 9, title: '漫画'),
      HomeTabItem(type: 31, title: '新书'),
      HomeTabItem(type: 15, title: '商城'),
    ];

    test(
      'a tab is requested with its own tab_type, not one from 本地表',
      () async {
        final requested = <int>[];
        final provider = NotifierProvider<HomeNotifier, HomeState>(
          () => HomeNotifier(
            homepageLoader:
                ({int tabType = 2, int offset = 0, String? sessionId}) async {
                  requested.add(tabType);
                  return HomepagePage(
                    items: [_item('book-$tabType')],
                    nextOffset: null,
                    sessionId: null,
                    tabs: serverTabs,
                  );
                },
            searchLoader: (query, {int page = 1}) async => [],
          ),
        );
        final container = ProviderContainer();
        addTearDown(container.dispose);
        final notifier = container.read(provider.notifier);
        // 首屏「推荐」= tab_type 2。
        await notifier.load();
        for (final index in [2, 3, 5, 8, 10]) {
          notifier.selectTab(index);
          await _flushMicrotasks();
        }

        // 每个下标的 tab_type 都来自它自己那一项：推荐 2、经典 11、知识 12、
        // 看剧 8、漫画 9、商城 15。若退回本地表则会是 2、24、5、16 —— 全部串台。
        expect(requested, [2, 11, 12, 8, 9, 15]);
      },
    );

    test(
      'the identity of a server tab keeps its kind and skips gender tabs',
      () async {
        final provider = NotifierProvider<HomeNotifier, HomeState>(
          () => HomeNotifier(
            homepageLoader:
                ({int tabType = 2, int offset = 0, String? sessionId}) async =>
                    HomepagePage(
                      items: [_item('book-$tabType')],
                      nextOffset: null,
                      sessionId: null,
                      tabs: const [
                        HomeTabItem(type: 2, title: '推荐'),
                        HomeTabItem(type: 0, title: '女生'),
                        HomeTabItem(type: 9, title: '漫画'),
                      ],
                    ),
            searchLoader: (query, {int page = 1}) async => [],
          ),
        );
        final container = ProviderContainer();
        addTearDown(container.dispose);
        final notifier = container.read(provider.notifier);

        // 分类条还没回来时，下标只能按本地兜底表解释（首屏就是这样）。
        expect(notifier.identityOf(0), (name: '小说', kind: 'book', tabType: 2));

        await notifier.load();

        expect(notifier.identityOf(0), (name: '推荐', kind: 'book', tabType: 2));
        // 0/1 是官方 female/male 入口，没有对应的书城流，只能走搜索兜底。
        expect(notifier.identityOf(1), (
          name: '女生',
          kind: 'book',
          tabType: null,
        ));
        expect(notifier.identityOf(2), (name: '漫画', kind: 'manga', tabType: 9));
      },
    );
  });
}

Future<void> _flushMicrotasks() async {
  await Future<void>.delayed(Duration.zero);
  await Future<void>.delayed(Duration.zero);
}

MediaItem _item(String id, {String kind = 'book', MediaTag? tag}) => MediaItem(
  id: id,
  title: id,
  cover: '',
  author: '',
  badge: '',
  ep: '',
  kind: kind,
  tag: tag,
);
