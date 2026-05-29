package project.ast;

public sealed interface Node permits Crate, Expression, Statement, Item, Block, BodyBlock {}
