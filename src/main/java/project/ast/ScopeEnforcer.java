package project.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import project.parser.RustParser;

/** A utility for checking that all nodes in a parse tree are in a permitted scope. */
public final class ScopeEnforcer {

  private final Set<Class<?>> PERMITTED =
      Set.of(
          RustParser.CrateContext.class,
          RustParser.ItemContext.class,
          RustParser.VisItemContext.class,
          RustParser.Function_Context.class,
          RustParser.FunctionQualifiersContext.class,
          RustParser.IdentifierContext.class,
          RustParser.BlockExpressionContext.class,
          RustParser.StatementContext.class,
          RustParser.StatementsContext.class,
          RustParser.LiteralExpression_Context.class,
          RustParser.PathExpression_Context.class,
          RustParser.ArithmeticOrLogicalExpressionContext.class,
          RustParser.PatternNoTopAltContext.class,
          RustParser.PatternWithoutRangeContext.class,
          RustParser.IdentifierPatternContext.class,
          RustParser.Type_Context.class,
          RustParser.TypeNoBoundsContext.class,
          RustParser.TraitObjectTypeOneBoundContext.class,
          RustParser.TraitBoundContext.class,
          RustParser.TypePathContext.class,
          RustParser.TypePathSegmentContext.class,
          RustParser.PathExpressionContext.class,
          RustParser.PathInExpressionContext.class,
          RustParser.PathExprSegmentContext.class,
          RustParser.PathIdentSegmentContext.class,
          RustParser.LiteralExpressionContext.class,
          RustParser.LetStatementContext.class);

  // TODO: Generalise this class to accept a permitted scope.
  // public ScopeEnforcer(Set<Class<?>> permitted) {
  //   PERMITTED = permitted;
  // }

  public List<Diagnostic> check(ParseTree tree) {
    List<Diagnostic> violations = new ArrayList<>();
    walk(tree, violations);
    return violations;
  }

  private void walk(ParseTree node, List<Diagnostic> violations) {
    if (node instanceof ParserRuleContext ctx && !PERMITTED.contains(ctx.getClass())) {
      violations.add(Diagnostic.unsupported(ctx));
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      walk(node.getChild(i), violations);
    }
  }
}
