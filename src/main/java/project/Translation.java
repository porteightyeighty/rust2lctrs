package project;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import project.ast.AstBuilder;
import project.ast.Crate;
import project.ast.Diagnostic;
import project.ast.DiagnosticRecorder;
import project.ast.SpanTable;
import project.ast.UnsupportedConstructException;
import project.lctrs.Lctrs;
import project.lctrs.Serialiser;
import project.lctrs.Simplifier;
import project.parser.RustParsing;
import project.parser.SyntaxErrorException;
import project.translator.IntegerSemantics;
import project.translator.Translator;

/**
 * The parse → AST → translate pipeline, shared by the CLI and the HTTP server. Reads Rust source
 * text and returns either the serialised LCTRS or the reason it could not be produced.
 */
public final class Translation {

  private static final Logger LOG = LoggerFactory.getLogger(Translation.class);

  /** Why a translation produced no LCTRS. */
  public enum Failure {
    NONE,
    OUT_OF_SCOPE,
    SYNTAX_ERROR
  }

  /**
   * The outcome of a translation. On {@link Failure#NONE} {@code lctrs} holds the serialised system
   * and {@code diagnostics} is empty; otherwise {@code lctrs} is {@code null} and {@code
   * diagnostics} holds the rendered messages, one per line.
   */
  public record Result(String lctrs, List<String> diagnostics, Failure failure) {}

  private Translation() {}

  /**
   * Translates Rust source into a serialised LCTRS.
   *
   * @param source the Rust source text
   * @param semantics the integer semantics to translate under
   * @param raw whether to skip the simplification passes
   * @param label the source name used in the translation log line (a path for the CLI)
   * @return the translation outcome; never throws for out-of-scope or malformed source
   */
  public static Result of(String source, IntegerSemantics semantics, boolean raw, String label) {
    DiagnosticRecorder diagnostics = new DiagnosticRecorder();
    SpanTable spanTable = new SpanTable();
    try {
      Crate crate = new AstBuilder(spanTable, diagnostics).buildCrate(RustParsing.parse(source));
      List<Diagnostic> recorded = diagnostics.diagnostics();
      if (!recorded.isEmpty()) {
        return new Result(
            null, recorded.stream().map(Diagnostic::toString).toList(), Failure.OUT_OF_SCOPE);
      }
      LOG.trace("Crate: {}", crate);
      Lctrs lctrs = new Translator(crate, spanTable, semantics).translate();
      if (!raw) {
        lctrs = Simplifier.simplify(lctrs);
      }
      LOG.info(
          "Translated {}: {} symbols, {} rules", label, lctrs.sigma().size(), lctrs.rules().size());
      return new Result(Serialiser.serialise(lctrs), List.of(), Failure.NONE);
    } catch (UnsupportedConstructException e) {
      // Some out-of-scope constructs can only be recognised once sorts are known, so the translator
      // rejects them past the AST boundary.
      return new Result(null, List.of(e.getMessage()), Failure.OUT_OF_SCOPE);
    } catch (SyntaxErrorException e) {
      return new Result(null, List.of(e.getMessage()), Failure.SYNTAX_ERROR);
    }
  }
}
