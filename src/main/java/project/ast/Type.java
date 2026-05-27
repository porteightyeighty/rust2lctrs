package project.ast;

/**
 * Represents a type in the supported fragment. Currently only fixed-width integer types are
 * supported.
 */
public sealed interface Type permits Type.Int {

  /** The set of primitive integer types recognised by the builder. */
  enum Int implements Type {
    i8,
    i16,
    i32,
    i64,
    i128,
    u8,
    u16,
    u32,
    u64,
    u128
  }
}
