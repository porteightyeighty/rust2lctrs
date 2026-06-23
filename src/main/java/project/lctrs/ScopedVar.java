package project.lctrs;

import project.ast.Type;

public record ScopedVar(VarDecl varDecl, Type sourceType) {}
