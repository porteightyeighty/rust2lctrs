package project.ast;

import java.util.Objects;

/**
 * A variable reference expression.
 *
 * @param name the identifier being referenced
 */
public record Variable(Identifier name) implements Expression {
  public Variable {
    Objects.requireNonNull(name);
  }
}
