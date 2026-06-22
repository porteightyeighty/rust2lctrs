package project.parser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import project.parser.RustParser.CrateContext;

/**
 * The single entry point for parsing Rust source into a parse tree. Both the CLI and the test
 * harness go through here so the throwing error listener is installed in exactly one place, and a
 * syntax error fails the pipeline fast instead of recovering into a malformed tree.
 *
 * <p>The {@code crate} grammar rule is anchored with {@code EOF}, so trailing unconsumed input also
 * surfaces as a syntax error here.
 */
public final class RustParsing {

  private RustParsing() {}

  /**
   * Parses Rust source into a {@code crate} parse tree, failing loudly on the first lexer or parser
   * syntax error.
   *
   * @param source the Rust source text
   * @return the parsed {@code crate} context
   * @throws SyntaxErrorException if the source is not well-formed Rust
   */
  public static CrateContext parse(String source) {
    CharStream input = CharStreams.fromString(source);
    RustLexer lexer = new RustLexer(input);
    lexer.removeErrorListeners();
    lexer.addErrorListener(ThrowingErrorListener.INSTANCE);

    RustParser parser = new RustParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(ThrowingErrorListener.INSTANCE);

    return parser.crate();
  }
}
