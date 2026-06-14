package project.lctrs;

import project.ast.Type;

/** A sort in the many-sorted LCTRS signature. */
public enum Sort {
  /** The integer sort. */
  INT("Int"),
  /** The boolean sort. */
  BOOL("Bool"),
  /** The non-theory sort. */
  A("A");

  private final String notation;

  Sort(String notation) {
    this.notation = notation;
  }

  /**
   * Returns this sort's spelling in Cora's input format.
   *
   * @return the Cora notation for this sort
   */
  public String notation() {
    return notation;
  }

  /**
   * Maps an AST {@link Type} to its corresponding LCTRS sort.
   *
   * @param type the source-language type
   * @return the sort encoding that type: {@link #INT} for integers, {@link #BOOL} for booleans
   */
  public static Sort of(Type type) {
    return switch (type) {
      case Type.Int _ -> INT;
      case Type.Bool _ -> BOOL;
    };
  }
}
