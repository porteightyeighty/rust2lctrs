package project.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import project.ast.AstBuilder;
import project.ast.Crate;
import project.ast.Diagnostic;
import project.ast.DiagnosticRecorder;
import project.ast.SpanTable;
import project.ast.UnsupportedConstructException;
import project.lctrs.Lctrs;
import project.lctrs.Serialiser;
import project.parser.RustParsing;
import project.parser.SyntaxErrorException;
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
   * @return {@code 0} on success, {@code 1} for I/O failure, {@code 2} for out-of-scope Rust,
   *     {@code 3} for malformed (unparseable) Rust
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

    DiagnosticRecorder diagnostics = new DiagnosticRecorder();
    SpanTable spanTable = new SpanTable();
    String result;
    try {
      Crate crate = buildCrate(source, spanTable, diagnostics);
      List<Diagnostic> recorded = diagnostics.diagnostics();
      if (!recorded.isEmpty()) {
        recorded.forEach(d -> LOG.error("Out-of-scope Rust: {}", d));
        return 2;
      }
      Lctrs lctrs = new Translator(crate, spanTable).translate();
      LOG.info(
          "Translated {}: {} symbols, {} rules",
          inputPath,
          lctrs.sigma().size(),
          lctrs.rules().size());
      result = Serialiser.serialise(lctrs);
    } catch (UnsupportedConstructException e) {
      // Some out-of-scope constructs can only be recognised once sorts are known, so the translator
      // rejects them past the AST boundary. Treat them as the same out-of-scope failure as an
      // AST-time diagnostic rather than letting the exception escape as an unhandled crash.
      LOG.error("Out-of-scope Rust: {}", e.getMessage());
      return 2;
    } catch (SyntaxErrorException e) {
      LOG.error("Malformed Rust: {}", e.getMessage());
      return 3;
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
   * Runs the parse → AST pipeline on a Rust source string, collecting any out-of-scope constructs
   * into the given recorder rather than throwing on the first.
   *
   * @param source the Rust source text
   * @param spanTable the table that node source spans are written to during the walk
   * @param diagnostics the recorder that out-of-scope-construct diagnostics are collected into
   * @return the built crate, which may be incomplete if {@code diagnostics} is non-empty
   */
  private static Crate buildCrate(
      String source, SpanTable spanTable, DiagnosticRecorder diagnostics) {
    AstBuilder astBuilder = new AstBuilder(spanTable, diagnostics);
    return astBuilder.buildCrate(RustParsing.parse(source));
  }
}
