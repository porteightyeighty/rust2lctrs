package project.ast;

import java.util.List;

public final record FunctionDef(
    Identifier identifier, List<Parameter> parameters, Block block, Type returnType)
    implements Item {}
