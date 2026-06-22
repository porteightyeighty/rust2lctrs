package project.parser;

/**
 * Thrown when the lexer or parser reports a syntax error, i.e. the input is not well-formed Rust.
 *
 * <p>This is distinct from {@code UnsupportedConstructException}, which rejects well-formed Rust
 * that falls outside the supported fragment. A {@code SyntaxErrorException} means the source did
 * not parse at all; the pipeline fails fast rather than recovering and lowering a malformed tree.
 */
public class SyntaxErrorException extends RuntimeException {

  /** 1-based line of the first syntax error. */
  private final int line;

  /** 0-based column of the first syntax error. */
  private final int column;

  /**
   * Constructs an exception for a syntax error at the given source position.
   *
   * @param line the 1-based line of the offending token
   * @param column the 0-based column of the offending token
   * @param message the recogniser's description of the error
   */
  public SyntaxErrorException(int line, int column, String message) {
    super("Syntax error at line %d:%d - %s".formatted(line, column, message));
    this.line = line;
    this.column = column;
  }

  /**
   * Returns the 1-based line of the offending token.
   *
   * @return the line number
   */
  public int line() {
    return line;
  }

  /**
   * Returns the 0-based column of the offending token.
   *
   * @return the column number
   */
  public int column() {
    return column;
  }
}
