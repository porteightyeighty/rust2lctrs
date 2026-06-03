package project.lctrs;

import project.ast.Type;

/** A sort in the many-sorted LCTRS signature. */
public enum Sort {
  /** The integer sort. */
  INT,
  /** The boolean sort. */
  BOOL;

  public static Sort of(Type type) {
    return switch (type) {
      case Type.Int _ -> INT;
      case Type.Bool _ -> BOOL;
    };
  }
}
