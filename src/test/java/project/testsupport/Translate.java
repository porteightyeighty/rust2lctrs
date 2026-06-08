package project.testsupport;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import project.ast.AstBuilder;
import project.ast.Crate;
import project.ast.SpanTable;
import project.lctrs.Serialiser;
import project.parser.RustLexer;
import project.parser.RustParser;
import project.translator.Translator;

/**
 * Drives the full parse → AST → translate → serialise pipeline on a Rust source string, so the
 * snapshot and e2e layers feed on exactly what the real tool would produce. Mirrors the chain in
 * {@code project.cli.Rust2LctrsCommand}; kept in test support because the tests live outside that
 * package and only see public API.
 */
public final class Translate {

  private Translate() {}

  /**
   * Translates Rust source to its serialised LCTRS form.
   *
   * @param source the Rust source text (assumed valid, in-scope Rust)
   * @return the LCTRS rendered in Cora's input format
   */
  public static String toLctrs(String source) {
    CharStream inputStream = CharStreams.fromString(source);
    RustLexer lexer = new RustLexer(inputStream);
    TokenStream tokens = new CommonTokenStream(lexer);
    RustParser parser = new RustParser(tokens);
    AstBuilder astBuilder = new AstBuilder(new SpanTable());
    Crate crate = astBuilder.buildCrate(parser.crate());
    return Serialiser.serialise(new Translator(crate).translate());
  }
}
