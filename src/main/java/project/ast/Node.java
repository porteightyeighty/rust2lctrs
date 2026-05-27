package project.ast;

public sealed interface Node permits Crate, Expr, Stmt, Item, Block {}
