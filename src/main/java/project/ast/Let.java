package project.ast;

import java.util.Objects;

/**
 * A {@code let} binding statement. The type annotation is always required and the binding must be
 * initialised. Both implicit types and uninitialised bindings are rejected by the builder.
 *
 * @param name the binding target (a simple identifier in the supported fragment)
 * @param type the declared type
 * @param value the initialiser expression
 */
public record Let(Identifier name, Type type, Expr value) implements Stmt {
  public Let {
    Objects.requireNonNull(name);
    Objects.requireNonNull(type);
    Objects.requireNonNull(value);
  }
}
