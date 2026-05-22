package project.ast;

public record BinOp(Op operator, Expr left, Expr right) {
  enum Op {
    ADD,
    SUB,
    MUL,
    EQ,
  }
}
