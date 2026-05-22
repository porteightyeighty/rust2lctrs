package project.ast;

public sealed interface Type permits Type.Int {
  record Int(IntSize size) implements Type {}

  enum IntSize {
    I8,
    I16,
    I32,
    I64,
    I128,
    U8,
    U16,
    U32,
    U64,
    U128
  }
}
