import 'package:flutter/material.dart';
import 'package:flutter_lucide/flutter_lucide.dart';


import '../home/home_design.dart';

/// Five stars filled to [stars] (halves supported), used by review tiles.
///
/// Lucide strokes, not the Material star font, so every icon on the detail
/// page shares one family; e-ink keeps the same shapes with palette tokens.
class DetailStarRow extends StatelessWidget {
  final double stars;
  final double size;

  const DetailStarRow({super.key, required this.stars, this.size = 11});

  @override
  Widget build(BuildContext context) {
    final palette = HomePalette.of(context);
    final filled = palette.eink ? palette.ink : HomePalette.accent;
    final empty = palette.eink ? palette.surface : palette.line;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var index = 1; index <= 5; index++)
          Builder(
            builder: (context) {
              final full = stars >= index;
              final half = !full && stars >= index - 0.5;
              return Icon(
                half ? LucideIcons.star_half : LucideIcons.star,
                size: size,
                color: full || half ? filled : empty,
              );
            },
          ),
      ],
    );
  }
}

/// Genre tags as rounded chips.
class DetailTagChips extends StatelessWidget {
  final List<String> tags;
  final bool desktop;

  const DetailTagChips({super.key, required this.tags, this.desktop = false});

  @override
  Widget build(BuildContext context) {
    final palette = HomePalette.of(context);
    if (tags.isEmpty) return const SizedBox.shrink();
    return Wrap(
      key: const Key('detail_tag_chips'),
      spacing: 6,
      runSpacing: 6,
      children: [
        for (final tag in tags)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 2),
            decoration: BoxDecoration(
              color: palette.soft,
              borderRadius: BorderRadius.circular(999),
              border: palette.eink ? Border.all(color: palette.line) : null,
            ),
            child: Text(
              tag,
              style: TextStyle(
                color: palette.muted,
                fontSize: desktop ? 13 : 11,
                height: 1.5,
              ),
            ),
          ),
      ],
    );
  }
}

