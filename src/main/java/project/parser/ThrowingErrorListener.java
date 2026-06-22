package project.parser;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * An ANTLR error listener that turns the first lexer or parser syntax error into a {@link
 * SyntaxErrorException} instead of printing to {@code stderr} and recovering. Installed on the
 * lexer and parser by {@link RustParsing} so that malformed input fails fast rather than being
 * lowered as a garbage parse tree.
 */
public final class ThrowingErrorListener extends BaseErrorListener {

  /** Shared stateless instance. */
  public static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

  private ThrowingErrorListener() {}

  @Override
  public void syntaxError(
      Recognizer<?, ?> recognizer,
      Object offendingSymbol,
      int line,
      int charPositionInLine,
      String msg,
      RecognitionException e) {
    throw new SyntaxErrorException(line, charPositionInLine, msg);
  }
}
