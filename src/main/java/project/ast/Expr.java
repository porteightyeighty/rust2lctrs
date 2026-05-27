package project.ast;

/** Marker interface for expressions. */
public sealed interface Expr permits IntLit, BinOp {}
