package project.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import project.testsupport.Verdict;

/**
 * Test-only driver for the external <a href="https://github.com/hezzel/cora">Cora</a> binary. The
 * e2e layer translates a Rust program, writes the LCTRS to a file, hands it to Cora, and asserts
 * the termination verdict.
 *
 * <p>Cora is an optional, externally-installed dependency (it needs Z3 too), so nothing here
 * assumes it is present: {@link #isAvailable()} lets the e2e tests {@code assumeTrue}-skip on
 * machines and CI runners that have not installed it. The binary is located via the {@code
 * CORA_BIN} environment variable (an absolute path or a bare command name) and falls back to {@code
 * cora} on {@code PATH}, so neither the tests nor CI hard-code an install location.
 */
final class Cora {

  private Cora() {}

  /** How long a single Cora invocation may run before the test gives up on it. */
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  /**
   * Whether a runnable Cora binary can be found.
   *
   * @return {@code true} if {@link #resolveBinary()} locates an executable
   */
  static boolean isAvailable() {
    return resolveBinary().isPresent();
  }

  /**
   * Locates the Cora executable. Honours {@code CORA_BIN} first: if it contains a path separator it
   * is treated as a direct path, otherwise it (default {@code cora}) is searched for on {@code
   * PATH}.
   *
   * @return the executable's path, or empty if none is found
   */
  static Optional<Path> resolveBinary() {
    String bin = System.getenv().getOrDefault("CORA_BIN", "cora");
    if (bin.contains(File.separator)) {
      Path direct = Path.of(bin);
      return Files.isExecutable(direct) ? Optional.of(direct) : Optional.empty();
    }
    String path = System.getenv("PATH");
    if (path == null) {
      return Optional.empty();
    }
    for (String dir : path.split(File.pathSeparator)) {
      if (dir.isEmpty()) {
        continue;
      }
      Path candidate = Path.of(dir, bin);
      if (Files.isExecutable(candidate)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  /**
   * Runs Cora on an LCTRS file and returns the verdict it prints.
   *
   * @param lctrsFile the LCTRS file to analyse
   * @return the parsed verdict
   * @throws IllegalStateException if Cora cannot be located, the process times out, or it exits
   *     before producing output
   * @throws IOException if the process cannot be started or its output cannot be read
   * @throws InterruptedException if the calling thread is interrupted while waiting
   */
  static Verdict run(Path lctrsFile) throws IOException, InterruptedException {
    Path bin =
        resolveBinary()
            .orElseThrow(() -> new IllegalStateException("Cora binary not found; set CORA_BIN"));

    Process proc =
        new ProcessBuilder(bin.toString(), lctrsFile.toString()).redirectErrorStream(true).start();

    String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (!proc.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
      proc.destroyForcibly();
      throw new IllegalStateException("Cora timed out after " + TIMEOUT.toSeconds() + "s");
    }
    return parse(output);
  }

  /**
   * Extracts the verdict from Cora's output: the first token of its first non-blank line.
   *
   * @param output Cora's combined stdout/stderr
   * @return the matching {@link Verdict}, or {@link Verdict#UNKNOWN}
   */
  static Verdict parse(String output) {
    return output
        .lines()
        .map(String::strip)
        .filter(line -> !line.isEmpty())
        .findFirst()
        .map(line -> line.split("\\s+", 2)[0].toUpperCase(Locale.ROOT))
        .map(
            first ->
                switch (first) {
                  case "YES" -> Verdict.YES;
                  case "MAYBE" -> Verdict.MAYBE;
                  case "NO" -> Verdict.NO;
                  default -> Verdict.UNKNOWN;
                })
        .orElse(Verdict.UNKNOWN);
  }
}
