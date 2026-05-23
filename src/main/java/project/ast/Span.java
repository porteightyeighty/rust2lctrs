package project.ast;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

record Span(int line, int col, int startIndex, int endIndex) {
  static Span of(ParserRuleContext ctx) {
    Token a = ctx.getStart();
    Token b = ctx.getStop();
    return new Span(a.getLine(), b.getCharPositionInLine(), a.getStartIndex(), b.getStopIndex());
  }

  @Override
  public final String toString() {
    return String.format("L%d:C%d - Span: [%d:%d]", line, col, startIndex, endIndex);
  }
}
