package project.ast;

import java.math.BigInteger;

/**
 * Represents a type in the supported fragment. Currently only fixed-width integer types are
 * supported.
 */
public sealed interface Type permits Type.Int, Type.Bool {

  Type.Bool BOOL = Type.Bool.bool;

  /** The set of primitive integer types recognised by the builder. */
  enum Int implements Type {
    i8(8, true),
    i16(16, true),
    i32(32, true),
    i64(64, true),
    i128(128, true),
    u8(8, false),
    u16(16, false),
    u32(32, false),
    u64(64, false),
    u128(128, false);

    private final int bits;
    private final boolean signed;

    Int(int bits, boolean signed) {
      this.bits = bits;
      this.signed = signed;
    }

    /**
     * Returns the smallest value representable by this integer type: {@code -2^(bits-1)} for signed
     * types, {@code 0} for unsigned.
     *
     * @return the inclusive lower bound of the representable range
     */
    public BigInteger min() {
      return signed ? BigInteger.TWO.pow(bits - 1).negate() : BigInteger.ZERO;
    }

    /**
     * Returns the largest value representable by this integer type: {@code 2^(bits-1) - 1} for
     * signed types, {@code 2^bits - 1} for unsigned.
     *
     * @return the inclusive upper bound of the representable range
     */
    public BigInteger max() {
      return BigInteger.TWO.pow(signed ? bits - 1 : bits).subtract(BigInteger.ONE);
    }
  }

  /** The boolean type. */
  enum Bool implements Type {
    bool
  }
}
