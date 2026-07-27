package project.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import project.translator.Profile;

/**
 * Discovers the shared benchmark corpus under {@code src/test/resources/benchmarks}.
 *
 * <p>The corpus is read from the source tree (not the classpath) so that the golden files the
 * snapshot layer writes in update mode land in the git-tracked originals rather than in a copy
 * under {@code target/}. Tests run with the module root as their working directory, so the relative
 * path resolves.
 *
 * <p>A source declares which overflow profiles to translate under, and the Cora verdict expected of
 * each, with per-profile markers {@code // debug: <verdict>} and {@code // release: <verdict>}.
 * Each marker present yields one {@link Benchmark}, whose golden lands in the matching profile
 * subdirectory ({@code debug/} or {@code release/}). So a single {@code .rs} can drive both
 * profiles with different expected verdicts. The verdict is optional: a bare {@code // debug:}
 * translates under debug but is snapshot-only (no e2e check). A source with no profile marker at
 * all defaults to a single, snapshot-only debug benchmark.
 */
public final class Benchmarks {

  /**
   * Source-tree location of the corpus, relative to the module root (the test working directory).
   */
  public static final Path DIR = Path.of("src", "test", "resources", "benchmarks");

  /** Shape of a profile marker, used only to detect one whose profile name is misspelt. */
  private static final Pattern PROFILE_MARKER = Pattern.compile("^//\\s*(\\w+):.*");

  private Benchmarks() {}

  /**
   * Loads every benchmark in the corpus, ordered by source name then profile.
   *
   * @return one {@link Benchmark} per profile marker on each {@code .rs} file (its golden under the
   *     profile subdirectory need not exist yet); one debug benchmark for an unmarked source
   * @throws UncheckedIOException if the corpus directory cannot be read
   */
  public static List<Benchmark> all() {
    try (Stream<Path> entries = Files.list(DIR)) {
      return entries
          .filter(p -> p.getFileName().toString().endsWith(".rs"))
          .sorted()
          .flatMap(Benchmarks::load)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read benchmark corpus at " + DIR.toAbsolutePath(), e);
    }
  }

  private static Stream<Benchmark> load(Path rust) {
    String fileName = rust.getFileName().toString();
    String name = fileName.substring(0, fileName.length() - ".rs".length());
    List<String> lines = readAllLines(rust);
    rejectUnknownProfiles(rust, lines);

    List<Benchmark> found = new ArrayList<>();
    for (Profile profile : Profile.values()) {
      marker(lines, "// " + profile.name() + ":")
          .ifPresent(verdict -> found.add(benchmark(name, rust, profile, verdict)));
    }
    if (found.isEmpty()) {
      found.add(benchmark(name, rust, Profile.debug, ""));
    }
    return found.stream();
  }

  private static Benchmark benchmark(String name, Path rust, Profile profile, String verdict) {
    Path golden = DIR.resolve(profile.name()).resolve(name + ".lctrs");
    return new Benchmark(name, rust, golden, parseVerdict(rust, verdict), profile);
  }

  /**
   * Turns a marker's value into an expected verdict. A blank value means snapshot-only. {@link
   * Verdict#UNKNOWN} is rejected: it is the sentinel for output Cora's parser did not recognise, so
   * declaring it would let a benchmark pass by producing garbage.
   */
  private static Optional<Verdict> parseVerdict(Path rust, String verdict) {
    if (verdict.isBlank()) {
      return Optional.empty();
    }
    Verdict parsed;
    try {
      parsed = Verdict.valueOf(verdict);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown verdict '"
              + verdict
              + "' in "
              + rust
              + "; expected one of "
              + Arrays.toString(Verdict.values()),
          e);
    }
    if (parsed == Verdict.UNKNOWN) {
      throw new IllegalArgumentException(
          "Verdict UNKNOWN cannot be expected in " + rust + "; it means Cora printed no verdict");
    }
    return Optional.of(parsed);
  }

  /**
   * Fails loudly on a marker whose profile name is not a {@link Profile}, so a typo like {@code //
   * releaes: NO} is caught rather than silently degrading the source to an unmarked, snapshot-only
   * debug benchmark.
   */
  private static void rejectUnknownProfiles(Path rust, List<String> lines) {
    for (String line : lines) {
      Matcher matcher = PROFILE_MARKER.matcher(line.strip());
      if (matcher.matches() && !isProfile(matcher.group(1))) {
        throw new IllegalArgumentException(
            "Unknown profile marker '// "
                + matcher.group(1)
                + ":' in "
                + rust
                + "; expected one of "
                + Arrays.toString(Profile.values()));
      }
    }
  }

  private static boolean isProfile(String name) {
    return Arrays.stream(Profile.values()).anyMatch(p -> p.name().equals(name));
  }

  private static List<String> readAllLines(Path rust) {
    try {
      return Files.readAllLines(rust);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read benchmark source " + rust, e);
    }
  }

  /**
   * Reads the value of the first {@code <prefix> <value>} marker, if present, uppercased so verdict
   * names match the {@link Verdict} enum. Scans the whole file (not just the first line) so markers
   * can sit wherever reads most naturally. A present-but-empty marker yields {@code ""}.
   */
  private static Optional<String> marker(List<String> lines, String prefix) {
    return lines.stream()
        .map(String::strip)
        .filter(line -> line.startsWith(prefix))
        .map(line -> line.substring(prefix.length()).strip().toUpperCase(Locale.ROOT))
        .findFirst();
  }
}
