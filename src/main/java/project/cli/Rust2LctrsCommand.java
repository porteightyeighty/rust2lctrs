package project.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import project.ast.AstBuilder;
import project.ast.Crate;
import project.ast.SpanTable;
import project.ast.UnsupportedConstructException;
import project.lctrs.Lctrs;
import project.lctrs.Serialiser;
import project.parser.RustLexer;
import project.parser.RustParser;
import project.translator.Translator;

/**
 * The {@code rust2lctrs} command: reads a Rust source file, runs it through the parse → AST →
 * translate pipeline, and serialises the resulting LCTRS to a file or stdout.
 */
@Command(
    name = "rust2lctrs",
    mixinStandardHelpOptions = true,
    description = "Converts a Rust program into a LCTRS program.")
public class Rust2LctrsCommand implements Callable<Integer> {

  private static final Logger LOG = LoggerFactory.getLogger(Rust2LctrsCommand.class);

  @Option(
      names = {"-o", "--output"},
      description = "output file; if omitted, the LCTRS is written to stdout")
  Path outputPath;

  @Parameters(index = "0", description = "Rust source file to translate")
  Path inputPath;

  /**
   * Reads the Rust source at {@code inputPath}, translates it to an LCTRS, and either writes the
   * result to {@code outputPath} or, when no output file is given, prints it to stdout so it can be
   * piped to the next command.
   *
   * @return {@code 0} on success, {@code 1} for I/O failure, {@code 2} for out-of-scope Rust
   */
  @Override
  public Integer call() {
    String source;
    try {
      source = Files.readString(inputPath);
    } catch (IOException e) {
      LOG.error("Could not read input file {}", inputPath, e);
      return 1;
    }

    String result;
    try {
      result = Serialiser.serialise(translate(source));
    } catch (UnsupportedConstructException e) {
      LOG.error("Out-of-scope Rust: {}", e.getMessage());
      return 2;
    }

    if (outputPath == null) {
      System.out.print(result);
      return 0;
    }

    try {
      Files.writeString(outputPath, result);
    } catch (IOException e) {
      LOG.error("Could not write output file {}", outputPath, e);
      return 1;
    }
    return 0;
  }

  /**
   * Runs the parse → AST → translate pipeline on a Rust source string.
   *
   * @param source the Rust source text
   * @return the translated LCTRS
   */
  private static Lctrs translate(String source) {
    CharStream inputStream = CharStreams.fromString(source);
    RustLexer lexer = new RustLexer(inputStream);
    TokenStream tokens = new CommonTokenStream(lexer);
    RustParser parser = new RustParser(tokens);
    SpanTable spanTable = new SpanTable();
    AstBuilder astBuilder = new AstBuilder(spanTable);
    Crate crate = astBuilder.buildCrate(parser.crate());
    return new Translator(crate).translate();
  }
}
