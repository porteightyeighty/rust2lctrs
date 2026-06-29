package project.lctrs;

import java.util.Objects;
import project.ast.Identifier;
import project.ast.Type;

/**
 * A variable that is live in the translation scope.
 *
 * @param sourceName the variable's identifier in the source program, used to resolve references and
 *     reassignments. a shadowing binding keeps its source identifier here while {@code varDecl}
 *     carries a freshly minted, scope-unique LCTRS name. A synthetic hoist variable reuses its own
 *     fresh LCTRS name here, so it too resolves by name.
 * @param varDecl the underlying LCTRS variable, whose name is unique among the live scope
 * @param sourceType the variable's source type, retained for overflow/sign analysis
 */
public record ScopedVar(Identifier sourceName, VarDecl varDecl, Type sourceType) {
  public ScopedVar {
    Objects.requireNonNull(sourceName, "sourceName");
    Objects.requireNonNull(varDecl, "varDecl");
    Objects.requireNonNull(sourceType, "sourceType");
  }
}
