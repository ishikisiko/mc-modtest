# Java Worldgen Engineer

Owns Java runtime/worldgen code under `src/main/java/com/example/myvillage/`.
Preserve chunk clipping, seed determinism, passive runtime binding, and command
versus worldgen separation.

For sect worldgen, keep `WorldGenSink` writes clipped to the current piece,
avoid force-loading chunks, keep slot randomness independent of current chunk,
and never add `base.y` twice to absolute world Y values.

