package project.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** The demo over real HTTP, on a server with no Cora install. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "cora.bin=/nonexistent/cora")
class DemoTest {

  static final HttpClient CLIENT = HttpClient.newHttpClient();

  static final String SUM =
      """
      fn sum(a: i8, b: i8) -> i8 {
          let x: i8 = a + b;
          x
      }
      """;

  @LocalServerPort int port;

  URI base;

  @BeforeEach
  void setBase() {
    base = URI.create("http://localhost:" + port);
  }

  static HttpResponse<String> post(URI base, String pathAndQuery, String source) throws Exception {
    return CLIENT.send(
        HttpRequest.newBuilder(base.resolve(pathAndQuery))
            .POST(HttpRequest.BodyPublishers.ofString(source))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path) throws Exception {
    return CLIENT.send(
        HttpRequest.newBuilder(base.resolve(path)).build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> translate(String source) throws Exception {
    return post(base, "/v1/translate?semantics=debug", source);
  }

  @Test
  void translatesAValidProgram() throws Exception {
    HttpResponse<String> response = translate(SUM);

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("sum"), response.body());
    assertEquals(
        "text/plain;charset=UTF-8", response.headers().firstValue("Content-Type").orElseThrow());
  }

  @Test
  void readsTheRawBodyWhateverTheContentType() throws Exception {
    // curl --data sends this type; the body must still reach the translator verbatim.
    HttpResponse<String> response =
        CLIENT.send(
            HttpRequest.newBuilder(base.resolve("/v1/translate?semantics=debug"))
                .POST(HttpRequest.BodyPublishers.ofString(SUM))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("sum"), response.body());
  }

  @Test
  void reportsOutOfScopeRust() throws Exception {
    HttpResponse<String> response =
        translate(
            """
            fn f(a: i8) -> i8 {
                for i in 0..10 {
                    a = a + 1;
                }
                a
            }
            """);

    assertEquals(422, response.statusCode());
    assertTrue(response.body().matches("(?s).*L\\d+:C\\d+.*"), response.body());
  }

  @Test
  void reportsMalformedRust() throws Exception {
    HttpResponse<String> response = translate("fn f( {");

    assertEquals(400, response.statusCode());
    assertTrue(response.body().contains("Syntax error"), response.body());
  }

  @Test
  void rejectsUnknownSemantics() throws Exception {
    HttpResponse<String> response = post(base, "/v1/translate?semantics=wrapping", SUM);

    assertEquals(400, response.statusCode());
    assertEquals("unknown semantics: wrapping", response.body());
  }

  @Test
  void rejectsOversizedBodies() throws Exception {
    HttpResponse<String> response = translate("a".repeat(64 * 1024 + 1));

    assertEquals(413, response.statusCode());
  }

  @Test
  void rejectsTheWrongMethod() throws Exception {
    HttpResponse<String> response = get("/v1/translate");

    assertEquals(405, response.statusCode());
    assertEquals("method not allowed", response.body());
  }

  @Test
  void answersPreflightWithCorsHeaders() throws Exception {
    HttpResponse<String> response =
        CLIENT.send(
            HttpRequest.newBuilder(base.resolve("/v1/analyse"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://example.org")
                .header("Access-Control-Request-Method", "POST")
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
  }

  @Test
  void exposesTheAnalysisTimingHeaderToBrowsers() throws Exception {
    HttpResponse<String> response =
        CLIENT.send(
            HttpRequest.newBuilder(base.resolve("/v1/translate"))
                .POST(HttpRequest.BodyPublishers.ofString(SUM))
                .header("Origin", "https://example.org")
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    assertTrue(
        response
            .headers()
            .firstValue("Access-Control-Expose-Headers")
            .orElseThrow()
            .contains("X-Analysis-Millis"));
  }

  @Test
  void answersAnalyseWith503WithoutCora() throws Exception {
    HttpResponse<String> response = post(base, "/v1/analyse?semantics=release", SUM);

    assertEquals(503, response.statusCode());
  }

  @Test
  void keepsTheLegacyHealthCheck() throws Exception {
    HttpResponse<String> response = get("/health");

    assertEquals(200, response.statusCode());
    assertEquals("ok", response.body());
  }

  @Test
  void reportsActuatorHealth() throws Exception {
    HttpResponse<String> response = get("/actuator/health");

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("\"UP\""), response.body());
  }

  @Test
  void exposesRequestMetricsForPrometheus() throws Exception {
    translate(SUM);

    HttpResponse<String> response = get("/actuator/prometheus");

    assertEquals(200, response.statusCode());
    assertTrue(
        response.body().contains("http_server_requests_seconds_count{")
            && response.body().contains("uri=\"/v1/translate\""),
        response.body());
  }

  @Test
  void exposesNoOtherActuatorEndpoints() throws Exception {
    assertEquals(404, get("/actuator/env").statusCode());
  }
}
