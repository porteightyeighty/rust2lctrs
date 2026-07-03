package project.translator;

/** The overflow semantics the translation encodes, mirroring rustc's compilation profiles. */
public enum Profile {
  /** Overflowing {@code +}, {@code -}, {@code *} panics: modelled as a rewrite to {@code err}. */
  debug,
  /**
   * Overflowing {@code +}, {@code -}, {@code *} and unary {@code -} wraps (two's complement),
   * encoded as {@code ((t − MIN) % 2^w) + MIN} over Cora's Euclidean {@code %} — exact for any
   * overflow magnitude, so a single wrap term covers {@code *} too. {@code /} and {@code %} panic
   * in both profiles, so their encoding is shared with {@link #debug}.
   */
  release
}
