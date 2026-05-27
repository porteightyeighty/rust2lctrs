package project.ast;

import java.util.Objects;

/**
 * A let binding. {@code type} is always present, implicit types are rejected at parse time. {@code
 * value} is guaranteed non-null; uninitialised bindings are not in the supported fragment.
 */
public record Let(Identifier name, Type type, Expr value) implements Stmt {
  public Let {
    Objects.requireNonNull(name);
    Objects.requireNonNull(type);
    Objects.requireNonNull(value);
  }
}
