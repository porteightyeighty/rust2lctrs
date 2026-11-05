package project.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code /v1/analyse} over real HTTP, with a stand-in for Cora wired in through {@code cora.bin}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoAnalyseTest {

  @LocalServerPort int port;

  URI base;

  @DynamicPropertySource
  static void fakeCora(DynamicPropertyRegistry registry) throws IOException {
    // Runs before the context starts, so it cannot use @TempDir.
    Path dir = Files.createTempDirectory("fake-cora-");
    dir.toFile().deleteOnExit();
    // Echoes the system it was given after a YES verdict.
    Path fakeCora = CoraRunnerTest.script(dir, "cora", "echo YES; cat \"$3\"");
    fakeCora.toFile().deleteOnExit();
    registry.add("cora.bin", fakeCora::toString);
  }

  @BeforeEach
  void setBase() {
    base = URI.create("http://localhost:" + port);
  }

  private HttpResponse<String> analyse(String source) throws Exception {
    return DemoTest.post(base, "/v1/analyse?semantics=release", source);
  }

  @Test
  void analysesTheSimplifiedTranslation() throws Exception {
    HttpResponse<String> response = analyse(DemoTest.SUM);

    assertEquals(200, response.statusCode(), response.body());
    assertTrue(response.body().startsWith("YES"), response.body());
    assertTrue(response.body().contains("sum"), response.body());
    assertTrue(response.headers().firstValue("X-Analysis-Millis").isPresent());
  }

  @Test
  void reportsOutOfScopeRustBeforeRunningCora() throws Exception {
    HttpResponse<String> response = analyse("fn f() -> f64 { 1.0 }");

    assertEquals(422, response.statusCode(), response.body());
  }

  @Test
  void recordsAnalysesByOutcome() throws Exception {
    analyse(DemoTest.SUM);

    String metrics =
        DemoTest.CLIENT
            .send(
                HttpRequest.newBuilder(base.resolve("/actuator/prometheus")).build(),
                HttpResponse.BodyHandlers.ofString())
            .body();

    assertTrue(metrics.contains("demo_analysis_seconds_count{outcome=\"finished\""), metrics);
  }
}
