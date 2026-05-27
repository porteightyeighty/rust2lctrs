package project.ast;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * The source location of a parse-tree node: its start line/column and the closed character-index
 * interval {@code [startIndex, endIndex]}.
 *
 * @param line 1-based line number of the first token
 * @param col 0-based column of the last token
 * @param startIndex character offset of the first token's start
 * @param endIndex character offset of the last token's end (inclusive)
 */
record Span(int line, int col, int startIndex, int endIndex) {

  /**
   * Constructs a {@code Span} from the first and last tokens of the given parser rule context.
   *
   * @param ctx the parse-tree node to extract the span from
   * @return the corresponding {@code Span}
   */
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
