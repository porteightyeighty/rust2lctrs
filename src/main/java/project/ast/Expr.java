package project.ast;

/** Marker interface for expression nodes in the AST. */
public sealed interface Expr permits IntLit, BinOp, Var {}
