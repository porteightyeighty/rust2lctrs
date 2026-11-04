package project.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import project.Translation;
import project.translator.IntegerSemantics;

/**
 * A small HTTP API in front of {@link Translation} for the web demo. Started as {@code java -cp
 * app.jar project.web.Demo}; the CLI entry point is unchanged.
 */
public final class Demo {

  private static final Logger LOG = LoggerFactory.getLogger(Demo.class);

  private static final int MAX_BODY = 64 * 1024;

  private Demo() {}

  public static void main(String[] args) throws IOException {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    String allowedOrigin = System.getenv().getOrDefault("ALLOWED_ORIGIN", "*");
    HttpServer server = start(port, allowedOrigin);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    LOG.info("Listening on {}", server.getAddress());
  }

  /**
   * Starts the demo server without blocking, so tests can bind an ephemeral port.
   *
   * @param port the port to bind, or {@code 0} for an ephemeral one
   * @param allowedOrigin the value sent as {@code Access-Control-Allow-Origin}
   * @return the started server
   */
  public static HttpServer start(int port, String allowedOrigin) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
    server.setExecutor(Executors.newFixedThreadPool(4));
    server.createContext("/health", exchange -> handleHealth(exchange, allowedOrigin));
    server.createContext("/v1/translate", exchange -> handleTranslate(exchange, allowedOrigin));
    server.start();
    return server;
  }

  private static void handleHealth(HttpExchange exchange, String allowedOrigin) throws IOException {
    if (!exchange.getRequestMethod().equals("GET")) {
      respond(exchange, 405, "method not allowed", allowedOrigin);
      return;
    }
    respond(exchange, 200, "ok", allowedOrigin);
  }

  private static void handleTranslate(HttpExchange exchange, String allowedOrigin)
      throws IOException {
    String method = exchange.getRequestMethod();
    if (method.equals("OPTIONS")) {
      setCors(exchange, allowedOrigin);
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
      return;
    }
    if (!method.equals("POST")) {
      respond(exchange, 405, "method not allowed", allowedOrigin);
      return;
    }

    Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
    String semanticsName = query.getOrDefault("semantics", "debug");
    IntegerSemantics semantics =
        switch (semanticsName) {
          case "debug" -> IntegerSemantics.debug;
          case "release" -> IntegerSemantics.release;
          case "unbounded" -> IntegerSemantics.unbounded;
          default -> null;
        };
    if (semantics == null) {
      respond(exchange, 400, "unknown semantics: " + semanticsName, allowedOrigin);
      return;
    }
    boolean raw = "1".equals(query.get("raw"));

    String source;
    try {
      source = readBody(exchange);
    } catch (BodyTooLargeException e) {
      respond(exchange, 413, "request body exceeds 64 KiB", allowedOrigin);
      return;
    }

    Translation.Result result = Translation.of(source, semantics, raw, "<http>");
    switch (result.failure()) {
      case NONE -> {
        LOG.info("POST /v1/translate {} {} bytes -> 200", semanticsName, source.length());
        respond(exchange, 200, result.lctrs(), allowedOrigin);
      }
      case OUT_OF_SCOPE -> {
        LOG.info("POST /v1/translate {} {} bytes -> 422", semanticsName, source.length());
        respond(exchange, 422, String.join("\n", result.diagnostics()), allowedOrigin);
      }
      case SYNTAX_ERROR -> {
        LOG.info("POST /v1/translate {} {} bytes -> 400", semanticsName, source.length());
        respond(exchange, 400, String.join("\n", result.diagnostics()), allowedOrigin);
      }
    }
  }

  /** Reads the request body, refusing anything over {@link #MAX_BODY}. */
  private static String readBody(HttpExchange exchange) throws IOException {
    String declared = exchange.getRequestHeaders().getFirst("Content-Length");
    if (declared != null && Long.parseLong(declared) > MAX_BODY) {
      throw new BodyTooLargeException();
    }
    try (InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int read;
      int total = 0;
      while ((read = in.read(buffer)) != -1) {
        total += read;
        if (total > MAX_BODY) {
          throw new BodyTooLargeException();
        }
        out.write(buffer, 0, read);
      }
      return out.toString(StandardCharsets.UTF_8);
    }
  }

  private static Map<String, String> parseQuery(String rawQuery) {
    Map<String, String> query = new HashMap<>();
    if (rawQuery == null || rawQuery.isEmpty()) {
      return query;
    }
    for (String pair : rawQuery.split("&")) {
      int eq = pair.indexOf('=');
      String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
      String value =
          eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
      query.put(key, value);
    }
    return query;
  }

  private static void setCors(HttpExchange exchange, String allowedOrigin) {
    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
  }

  private static void respond(HttpExchange exchange, int status, String body, String allowedOrigin)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    setCors(exchange, allowedOrigin);
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private static final class BodyTooLargeException extends RuntimeException {}
}
