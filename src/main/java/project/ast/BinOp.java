package project.ast;

import java.util.Objects;

public record BinOp(Op operator, Expr left, Expr right) implements Expr {
  public BinOp {
    Objects.requireNonNull(operator);
    Objects.requireNonNull(left);
    Objects.requireNonNull(right);
  }

  enum Op {
    ADD,
    SUB,
    MUL,
    DIV
  }
}
