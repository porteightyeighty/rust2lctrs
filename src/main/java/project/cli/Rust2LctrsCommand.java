package project.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.TypeConversionException;
import project.Translation;
import project.translator.IntegerSemantics;

/**
 * The {@code rust2lctrs} command: reads a Rust source file, runs it through the parse → AST →
 * translate pipeline, and serialises the resulting LCTRS to a file or stdout.
 */
@Command(
    name = "rust2lctrs",
    mixinStandardHelpOptions = true,
    versionProvider = Rust2LctrsCommand.ManifestVersion.class,
    description = "Converts a Rust program into a LCTRS program.")
public class Rust2LctrsCommand implements Callable<Integer> {

  private static final Logger LOG = LoggerFactory.getLogger(Rust2LctrsCommand.class);

  /**
   * Reports the version {@code --version} prints, read from the jar manifest. Falls back to a
   * placeholder when the classes are loaded outside a jar.
   */
  static class ManifestVersion implements IVersionProvider {

    @Override
    public String[] getVersion() {
      String version = Rust2LctrsCommand.class.getPackage().getImplementationVersion();
      return new String[] {"rust2lctrs " + (version == null ? "(dev build)" : version)};
    }
  }

  @Option(
      names = {"-o", "--output"},
      description = "output file; if omitted, the LCTRS is written to stdout")
  Path outputPath;

  @ArgGroup(exclusive = true)
  IntegerOpts ints = new IntegerOpts();

  /**
   * The two ways of picking integer semantics. {@code --profile} chooses a rustc profile; {@code
   * --unbounded} steps outside them, so picocli rejects the pair rather than letting one win.
   */
  static class IntegerOpts {

    @Option(
        names = "--profile",
        converter = ProfileConverter.class,
        description =
            "overflow semantics: debug (arithmetic overflow panics) or release (wraps, two's "
                + "complement). Default: ${DEFAULT-VALUE}.")
    IntegerSemantics profile = IntegerSemantics.debug;

    @Option(
        names = "--unbounded",
        description =
            "idealise integers to Z: no widths, so no overflow constraints and no wrapping. "
                + "Division by zero is still guarded. Termination verdicts then describe the "
                + "idealised model, not the Rust program.")
    boolean unbounded;

    IntegerSemantics semantics() {
      return unbounded ? IntegerSemantics.unbounded : profile;
    }
  }

  /**
   * Restricts {@code --profile} to the rustc profiles. The default enum converter would also accept
   * {@code unbounded}, which has no rustc counterpart and is reached through {@code --unbounded}.
   */
  static class ProfileConverter implements ITypeConverter<IntegerSemantics> {

    @Override
    public IntegerSemantics convert(String value) {
      return switch (value) {
        case "debug" -> IntegerSemantics.debug;
        case "release" -> IntegerSemantics.release;
        default ->
            throw new TypeConversionException(
                "expected one of [debug, release] but was '" + value + "'");
      };
    }
  }

  @Option(
      names = {"-r", "--raw"},
      description =
          "emit the rules as the statement-by-statement translation produces them "
              + "(skip the simplification passes: forwarding rules kept, constraints unfolded)")
  boolean raw;

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

    Translation.Result translation =
        Translation.of(source, ints.semantics(), raw, inputPath.toString());
    switch (translation.failure()) {
      case OUT_OF_SCOPE -> {
        translation.diagnostics().forEach(d -> LOG.error("Out-of-scope Rust: {}", d));
        return 2;
      }
      case SYNTAX_ERROR -> {
        translation.diagnostics().forEach(d -> LOG.error("Malformed Rust: {}", d));
        return 3;
      }
      case NONE -> {}
    }

    String result = translation.lctrs();
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
}
