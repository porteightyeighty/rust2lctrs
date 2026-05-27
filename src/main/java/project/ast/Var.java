package project.ast;

import java.util.Objects;

/**
 * A variable reference expression.
 *
 * @param name the identifier being referenced
 */
public record Var(Identifier name) implements Expr {
  public Var {
    Objects.requireNonNull(name);
  }
}
