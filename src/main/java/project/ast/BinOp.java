package project.ast;

public record BinOp(Op operator, Expr left, Expr right) implements Expr {
  enum Op {
    ADD,
    SUB,
    MUL,
    DIV,
    EQ,
  }
}
