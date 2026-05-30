package project.ast;

import java.util.Objects;
import java.util.Optional;

/**
 * An {@code if}/{@code else} statement.
 *
 * @param condition the branch condition, of type {@code bool}
 * @param thenBlock the block executed when the condition holds
 * @param elseBlock the block executed otherwise, empty when no {@code else} is present
 */
public record If(Expression condition, Block thenBlock, Optional<Block> elseBlock) implements Statement {
  public If {
    Objects.requireNonNull(condition);
    Objects.requireNonNull(thenBlock);
    Objects.requireNonNull(elseBlock);
  }
}
