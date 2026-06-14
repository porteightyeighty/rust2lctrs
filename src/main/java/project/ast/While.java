package project.ast;

import java.util.Objects;

/**
 * A {@code while} loop, executing its body while the condition holds.
 *
 * @param condition the loop guard, of type {@code bool}
 * @param block the loop body
 */
public record While(Expression condition, Block block) implements Statement {
  public While {
    Objects.requireNonNull(condition);
    Objects.requireNonNull(block);
  }
}
