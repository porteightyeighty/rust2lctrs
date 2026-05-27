package project.ast;

/** Marker interface for statement nodes that can appear inside a {@link Block}. */
public sealed interface Stmt extends Node permits LetStmt, Return {}
