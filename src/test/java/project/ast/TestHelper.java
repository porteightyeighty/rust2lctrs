package project.ast;

import java.util.function.Function;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import project.parser.RustLexer;
import project.parser.RustParser;

/**
 * Test-only utilities for parsing Rust fragments into ANTLR parse trees.
 *
 * <p>Each entry point starts parsing at a specific grammar rule, so a construct can be parsed in
 * isolation (e.g. a bare expression {@code 1 + 2}) without scaffolding it inside a function. The
 * resulting subtree is handed to the matching {@code AstBuilder.build*} method under test.
 *
 * <p>Parsing fails loudly: lexer/parser syntax errors throw {@link ParseException}, and any input
 * left unconsumed after the chosen rule also throws. A fragment that only partially parses can
 * therefore never reach the builder as a junk tree.
 */
public final class TestHelper {
  private TestHelper() {}

  /** Thrown when a fragment fails to parse cleanly or leaves trailing unconsumed input. */
  static final class ParseException extends RuntimeException {
    ParseException(String message) {
      super(message);
    }
  }

  static RustParser.StatementContext parseStmt(String src) {
    return parse(src, RustParser::statement);
  }

  /** Parses a single expression */
  static RustParser.ExpressionContext parseExpr(String src) {
    return parse(src, RustParser::expression);
  }

  /** Parses a whole crate. */
  static RustParser.CrateContext parseCrate(String src) {
    return parse(src, RustParser::crate);
  }

  static RustParser.BlockExpressionContext parseBlock(String src) {
    return parse(src, RustParser::blockExpression);
  }

  static RustParser.ItemContext parseItem(String src) {
    return parse(src, RustParser::item);
  }

  /**
   * Parses {@code src} starting at the given grammar rule, failing loudly on any syntax error or
   * trailing unconsumed input.
   *
   * @param src the Rust fragment
   * @param rule the parser rule to start from (e.g. {@code RustParser::expression})
   * @param <T> the parse-tree context type produced by the rule
   * @return the parsed subtree
   * @throws ParseException if the fragment has syntax errors or is not fully consumed
   */
  private static <T extends ParserRuleContext> T parse(String src, Function<RustParser, T> rule) {
    var lexer = new RustLexer(CharStreams.fromString(src));
    lexer.removeErrorListeners();
    lexer.addErrorListener(ThrowingErrorListener.INSTANCE);

    var parser = new RustParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(ThrowingErrorListener.INSTANCE);

    T tree = rule.apply(parser);

    Token next = parser.getCurrentToken();
    if (next.getType() != Token.EOF) {
      throw new ParseException(
          "Unconsumed input after parse; next token '%s' at line %d:%d"
              .formatted(next.getText(), next.getLine(), next.getCharPositionInLine()));
    }
    return tree;
  }

  /** Converts the first lexer/parser syntax error into a {@link ParseException}. */
  private static final class ThrowingErrorListener extends BaseErrorListener {
    static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e) {
      throw new ParseException(
          "Syntax error at line %d:%d - %s".formatted(line, charPositionInLine, msg));
    }
  }
}
