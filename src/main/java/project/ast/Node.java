package project.ast;

/**
 * Marker interface for all AST nodes produced by the parse-tree-to-AST visitor.
 *
 * <p>Nodes are either expressions ({@link Expr}) or statements ({@link Stmt}). All concrete node
 * types are records; the sealed hierarchy ensures exhaustive handling in switch expressions
 * throughout the translator.
 */
public sealed interface Node permits Expr, Stmt {}
