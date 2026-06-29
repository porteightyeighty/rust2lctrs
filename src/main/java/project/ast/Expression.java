package project.ast;

/** Marker interface for expression nodes in the AST. */
public sealed interface Expression extends Node permits Literal, BinaryOp, UnaryOp, Variable {}
