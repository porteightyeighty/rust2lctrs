package project.ast;

public sealed interface Expr extends Node permits IntLit, BinOp {}
