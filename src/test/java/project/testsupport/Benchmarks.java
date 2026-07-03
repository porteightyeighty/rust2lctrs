package project.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import project.translator.Profile;

/**
 * Discovers the shared benchmark corpus under {@code src/test/resources/benchmarks}.
 *
 * <p>The corpus is read from the source tree (not the classpath) so that the golden files the
 * snapshot layer writes in update mode land in the git-tracked originals rather than in a copy
 * under {@code target/}. Tests run with the module root as their working directory, so the relative
 * path resolves.
 */
public final class Benchmarks {

  /**
   * Source-tree location of the corpus, relative to the module root (the test working directory).
   */
  public static final Path DIR = Path.of("src", "test", "resources", "benchmarks");

  /** Marker that declares a benchmark's expected Cora verdict, e.g. {@code // cora: YES}. */
  private static final String VERDICT_MARKER = "// cora:";

  /** Marker that selects the overflow profile, e.g. {@code // profile: release}. */
  private static final String PROFILE_MARKER = "// profile:";

  private Benchmarks() {}

  /**
   * Loads every benchmark in the corpus, ordered by name.
   *
   * @return one {@link Benchmark} per {@code .rs} file, each paired with its sibling {@code .lctrs}
   *     golden (which need not exist yet) and parsed verdict marker
   * @throws UncheckedIOException if the corpus directory cannot be read
   */
  public static List<Benchmark> all() {
    try (Stream<Path> entries = Files.list(DIR)) {
      return entries
          .filter(p -> p.getFileName().toString().endsWith(".rs"))
          .sorted()
          .map(Benchmarks::load)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read benchmark corpus at " + DIR.toAbsolutePath(), e);
    }
  }

  private static Benchmark load(Path rust) {
    String fileName = rust.getFileName().toString();
    String name = fileName.substring(0, fileName.length() - ".rs".length());
    Path golden = rust.resolveSibling(name + ".lctrs");
    List<String> lines = readAllLines(rust);
    return new Benchmark(name, rust, golden, readVerdict(lines), readProfile(lines));
  }

  private static List<String> readAllLines(Path rust) {
    try {
      return Files.readAllLines(rust);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read benchmark source " + rust, e);
    }
  }

  /**
   * Reads the {@code // cora: <verdict>} marker, if present. Scans the whole file (not just the
   * first line) so the marker can sit wherever reads most naturally.
   */
  private static Optional<Verdict> readVerdict(List<String> lines) {
    return marker(lines, VERDICT_MARKER).map(Verdict::valueOf);
  }

  /**
   * Reads the {@code // profile: <profile>} marker, defaulting to {@link Profile#debug} when absent
   * so every existing (unmarked) benchmark keeps its byte-identical debug translation.
   */
  private static Profile readProfile(List<String> lines) {
    return marker(lines, PROFILE_MARKER)
        .map(v -> Profile.valueOf(v.toLowerCase(Locale.ROOT)))
        .orElse(Profile.debug);
  }

  private static Optional<String> marker(List<String> lines, String prefix) {
    return lines.stream()
        .map(String::strip)
        .filter(line -> line.startsWith(prefix))
        .map(line -> line.substring(prefix.length()).strip().toUpperCase(Locale.ROOT))
        .findFirst();
  }
}
