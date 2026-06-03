package project.ast;

import project.parser.RustParser.Type_Context;

/**
 * Reads {@link Type}s from type parse-tree contexts. Shared by the item and statement
 * builders, since types appear on function signatures, parameters, and {@code let} bindings alike.
 */
final class TypeReader {

  private TypeReader() {}

  /**
   * Reads a {@link Type} from a type context. Only the primitive integer types and {@code bool} are
   * supported.
   *
   * @param ctx the type context
   * @return the corresponding {@link Type}
   * @throws UnsupportedConstructException if the type is not a supported primitive
   */
  static Type read(Type_Context ctx) {
    String typeText = ctx.getText();
    return switch (typeText) {
      case "i8", "i16", "i32", "i64", "i128", "u8", "u16", "u32", "u64", "u128" ->
          Type.Int.valueOf(typeText);
      case "bool" -> Type.BOOL;
      default -> throw new UnsupportedConstructException(ctx, "Unsupported type: " + typeText);
    };
  }
}
