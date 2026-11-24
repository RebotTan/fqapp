import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:fqapp/models/media_item.dart';
import 'package:fqapp/pages/detail_page.dart';
import 'package:fqapp/services/chapter_text_formatter.dart';

import 'support/fakes.dart';

final _book = MediaItem(
  id: 'book',
  title: '测试书',
  cover: '',
  author: '',
  badge: '',
  ep: '',
  kind: 'book',
);

void main() {
  testWidgets('范围下载只缓存选定的一段章节', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final cache = MemoryChapterCache();
    final chapters = [
      for (var i = 0; i < 4; i++)
        Chapter(itemId: 'c$i', title: '第${i + 1}章', volumeName: ''),
    ];
    final fetched = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        home: DetailPage(
          item: _book,
          chapterCache: cache,
          readerStore: MemoryReaderStore(),
          detailLoader: (id, {String tab = '小说'}) async => {},
          directoryLoader: (id, {String tab = '小说'}) async => [chapters],
          chapterLoader: (chapter) async {
            fetched.add(chapter.itemId);
            return ChapterContent.fromPlainText(
              '正文${chapter.itemId}',
              illustrationsChecked: true,
            ).toCacheText();
          },
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('detail_action_下载')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('detail_download_range')));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const Key('detail_range_from')), '2');
    await tester.enterText(find.byKey(const Key('detail_range_to')), '3');
    await tester.tap(find.byKey(const Key('detail_range_confirm')));
    await tester.pumpAndSettle();

    expect(fetched, ['c1', 'c2']);
    final ids = <String>[];
    for (var i = 0; i < 4; i++) {
      if (await cache.read(bookId: 'book', chapterId: 'c$i') != null) {
        ids.add('c$i');
      }
    }
    expect(ids, ['c1', 'c2']);
  });

  testWidgets('范围越界时拒绝开始下载', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final cache = MemoryChapterCache();
    final chapters = [
      for (var i = 0; i < 4; i++)
        Chapter(itemId: 'c$i', title: '第${i + 1}章', volumeName: ''),
    ];
    final fetched = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        home: DetailPage(
          item: _book,
          chapterCache: cache,
          readerStore: MemoryReaderStore(),
          detailLoader: (id, {String tab = '小说'}) async => {},
          directoryLoader: (id, {String tab = '小说'}) async => [chapters],
          chapterLoader: (chapter) async {
            fetched.add(chapter.itemId);
            return '正文';
          },
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('detail_action_下载')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('detail_download_range')));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const Key('detail_range_from')), '3');
    await tester.enterText(find.byKey(const Key('detail_range_to')), '2');
    await tester.tap(find.byKey(const Key('detail_range_confirm')));
    await tester.pumpAndSettle();

    expect(fetched, isEmpty);
    expect(find.byType(AlertDialog), findsOneWidget);
  });
}