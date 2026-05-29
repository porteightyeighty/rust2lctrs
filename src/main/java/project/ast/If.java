package project.ast;

import java.util.Objects;
import java.util.Optional;

public record If(Expression condition, Block thenBlock, Optional<Block> elseBlock) implements Statement {
  public If {
    Objects.requireNonNull(condition);
    Objects.requireNonNull(thenBlock);
    Objects.requireNonNull(elseBlock);
  }
}
