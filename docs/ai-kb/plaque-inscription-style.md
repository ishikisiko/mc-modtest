# Plaque Inscription Style

The v1 inscription library uses one PNG per plaque name and bucket. Do not split characters into tiles.

## Registers

- Town shop signs use practical regular or running script, high contrast, and minimal seal use.
- Inns and taverns use warmer running script with a small red seal near the lower trailing side.
- Manor plaques use formal clerical or regular script, balanced spacing, and restrained heraldic seal placement.
- Sect scripture and treasure plaques use expressive semi-cursive or seal-influenced brushwork, with stronger dry-brush texture and a red seal that does not cover the main strokes.

## Composition

- Horizontal buckets (`3w`, `4w`, `5w_1h`, `5w_2h`) compose left-to-right across the full interior. Keep top and bottom margins clear of the frame.
- Vertical buckets (`3h`, `4h`, `5h`) compose top-to-bottom in a single column. Do not rotate horizontal art.
- `5w_2h` is reserved for grand 大字 compositions and may use larger strokes and more empty space.

## Resolution

Inscription PNGs use 32 to 128 pixels per Minecraft block of width. Town-tier art should usually use 32 px/block, civic and sect art 64 px/block, and grand sect 大字 up to 128 px/block. The PNG aspect ratio must exactly match its bucket.
