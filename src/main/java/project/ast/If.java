package project.ast;

import java.util.Objects;
import java.util.Optional;

public record If(Expr condition, Block thenBlock, Optional<Block> elseBlock) implements Stmt {
  public If {
    Objects.requireNonNull(condition);
    Objects.requireNonNull(thenBlock);
    Objects.requireNonNull(elseBlock);
  }
}
