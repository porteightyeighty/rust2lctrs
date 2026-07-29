package project.translator;

/**
 * The integer semantics the translation encodes: what happens at the width boundary. {@link #debug}
 * and {@link #release} mirror rustc's compilation profiles; {@link #unbounded} is a deliberate
 * idealisation with no rustc counterpart.
 */
public enum IntegerSemantics {
  /** Overflowing {@code +}, {@code -}, {@code *} panics: modelled as a rewrite to {@code err}. */
  debug,
  /**
   * Overflowing {@code +}, {@code -}, {@code *} and unary {@code -} wraps (two's complement),
   * encoded as {@code ((t − MIN) % 2^w) + MIN} over Cora's Euclidean {@code %} — exact for any
   * overflow magnitude, so a single wrap term covers {@code *} too. {@code /} and {@code %} panic
   * in both profiles, so their encoding is shared with {@link #debug}.
   */
  release,
  /**
   * Integers are the unbounded sort {@code Z}: no widths, so no overflow clauses and no wrapping.
   * Keeps examples readable when overflow isn't their point.
   *
   * <p>{@code /} and {@code %} still guard {@code divisor != 0} — that's partiality, not width. And
   * verdicts here describe the idealised model, not the Rust: a loop that terminates under {@link
   * #debug} only by overflowing into {@code err} may diverge over {@code Z}.
   */
  unbounded
}
