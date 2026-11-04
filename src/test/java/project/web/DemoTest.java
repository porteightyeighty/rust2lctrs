package project.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DemoTest {

  private static HttpServer server;
  private static URI base;
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  @BeforeAll
  static void startServer() throws Exception {
    server = Demo.start(0, "*");
    base = URI.create("http://localhost:" + server.getAddress().getPort());
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  private static HttpResponse<String> translate(String source) throws Exception {
    return CLIENT.send(
        HttpRequest.newBuilder(base.resolve("/v1/translate?semantics=debug"))
            .POST(HttpRequest.BodyPublishers.ofString(source))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void translatesAValidProgram() throws Exception {
    HttpResponse<String> response =
        translate(
            """
            fn sum(a: i8, b: i8) -> i8 {
                let x: i8 = a + b;
                x
            }
            """);

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
  void rejectsOversizedBodies() throws Exception {
    HttpResponse<String> response = translate("a".repeat(64 * 1024 + 1));

    assertEquals(413, response.statusCode());
  }

  @Test
  void answersPreflightWithCorsHeaders() throws Exception {
    HttpResponse<String> response =
        CLIENT.send(
            HttpRequest.newBuilder(base.resolve("/v1/translate"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

    assertEquals(204, response.statusCode());
    assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
  }
}
