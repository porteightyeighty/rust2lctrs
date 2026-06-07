package project.ast;

/** The root of the AST node hierarchy: every node in {@code project.ast} is a {@code Node}. */
public sealed interface Node permits Crate, Expression, Statement, Item, Block {}
