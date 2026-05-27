package project.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Block(List<Stmt> statements, Optional<Expr> trailingExpression) {
  public Block {
    Objects.requireNonNull(statements);
    Objects.requireNonNull(trailingExpression);
  }
}
