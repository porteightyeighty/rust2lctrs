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
import java.util.Optional;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import project.Translation;
import project.translator.IntegerSemantics;

/**
 * A small HTTP API in front of {@link Translation} for the web demo. Started as {@code java -cp
 * app.jar project.web.Demo}; the CLI entry point is unchanged.
 *
 * <p>{@code /v1/translate} returns the LCTRS. {@code /v1/analyse} translates and then runs Cora on
 * the result (see {@link CoraRunner}); it answers {@code 503} when the server has no Cora install.
 */
public final class Demo {

  private static final Logger LOG = LoggerFactory.getLogger(Demo.class);

  private static final int MAX_BODY = 64 * 1024;

  private Demo() {}

  public static void main(String[] args) throws IOException {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    String allowedOrigin = System.getenv().getOrDefault("ALLOWED_ORIGIN", "*");
    Optional<CoraRunner> cora = CoraRunner.fromEnvironment();
    if (cora.isEmpty()) {
      LOG.warn("Cora not found (set CORA_BIN): /v1/analyse will answer 503");
    }
    HttpServer server = start(port, allowedOrigin, cora);
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
    return start(port, allowedOrigin, Optional.empty());
  }

  /**
   * Starts the demo server without blocking, with termination analysis if {@code cora} is present.
   *
   * @param port the port to bind, or {@code 0} for an ephemeral one
   * @param allowedOrigin the value sent as {@code Access-Control-Allow-Origin}
   * @param cora the Cora runner behind {@code /v1/analyse}, or empty to answer it with {@code 503}
   * @return the started server
   */
  public static HttpServer start(int port, String allowedOrigin, Optional<CoraRunner> cora)
      throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
    server.setExecutor(Executors.newFixedThreadPool(8));
    server.createContext("/health", exchange -> handleHealth(exchange, allowedOrigin));
    server.createContext("/v1/translate", exchange -> handleTranslate(exchange, allowedOrigin));
    server.createContext("/v1/analyse", exchange -> handleAnalyse(exchange, allowedOrigin, cora));
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
    Optional<Request> request = readRequest(exchange, allowedOrigin);
    if (request.isEmpty()) {
      return;
    }
    Request req = request.get();
    Translation.Result result = Translation.of(req.source(), req.semantics(), req.raw(), "<http>");
    if (respondToFailure(exchange, "/v1/translate", req, result, allowedOrigin)) {
      return;
    }
    LOG.info("POST /v1/translate {} {} bytes -> 200", req.semanticsName(), req.source().length());
    respond(exchange, 200, result.lctrs(), allowedOrigin);
  }

  private static void handleAnalyse(
      HttpExchange exchange, String allowedOrigin, Optional<CoraRunner> cora) throws IOException {
    Optional<Request> request = readRequest(exchange, allowedOrigin);
    if (request.isEmpty()) {
      return;
    }
    Request req = request.get();
    if (cora.isEmpty()) {
      respond(exchange, 503, "termination analysis is not available on this server", allowedOrigin);
      return;
    }
    // Always analyse the simplified system: simplification preserves termination, and it is the
    // form the benchmarks are checked in.
    Translation.Result result = Translation.of(req.source(), req.semantics(), false, "<http>");
    if (respondToFailure(exchange, "/v1/analyse", req, result, allowedOrigin)) {
      return;
    }

    CoraRunner.Analysis analysis;
    try {
      analysis = cora.get().analyse(result.lctrs());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      respond(exchange, 503, "the server is shutting down", allowedOrigin);
      return;
    }
    exchange.getResponseHeaders().set("X-Analysis-Millis", Long.toString(analysis.millis()));
    int status =
        switch (analysis.outcome()) {
          case FINISHED -> 200;
          case BUSY -> 503;
          case TIMED_OUT -> 504;
          case FAILED -> 502;
        };
    String body =
        switch (analysis.outcome()) {
          case FINISHED, FAILED -> analysis.output();
          case BUSY -> "the analyser is busy; try again in a few seconds";
          case TIMED_OUT -> "Cora gave no answer within " + cora.get().timeout().toSeconds() + " s";
        };
    if (analysis.outcome() == CoraRunner.Outcome.BUSY) {
      exchange.getResponseHeaders().set("Retry-After", "5");
    }
    LOG.info(
        "POST /v1/analyse {} {} bytes -> {} in {} ms",
        req.semanticsName(),
        req.source().length(),
        status,
        analysis.millis());
    respond(exchange, status, body, allowedOrigin);
  }

  /** A parsed {@code POST} to one of the source-taking routes. */
  private record Request(
      String source, String semanticsName, IntegerSemantics semantics, boolean raw) {}

  /**
   * Handles CORS preflight, the method check, the query and the body shared by the routes that take
   * Rust source.
   *
   * @return the parsed request, or empty if a response has already been sent
   */
  private static Optional<Request> readRequest(HttpExchange exchange, String allowedOrigin)
      throws IOException {
    String method = exchange.getRequestMethod();
    if (method.equals("OPTIONS")) {
      setCors(exchange, allowedOrigin);
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
      return Optional.empty();
    }
    if (!method.equals("POST")) {
      respond(exchange, 405, "method not allowed", allowedOrigin);
      return Optional.empty();
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
      return Optional.empty();
    }
    boolean raw = "1".equals(query.get("raw"));

    try {
      return Optional.of(new Request(readBody(exchange), semanticsName, semantics, raw));
    } catch (BodyTooLargeException e) {
      respond(exchange, 413, "request body exceeds 64 KiB", allowedOrigin);
      return Optional.empty();
    }
  }

  /**
   * Answers a failed translation with {@code 422} (out of scope) or {@code 400} (syntax error).
   *
   * @return whether a response was sent
   */
  private static boolean respondToFailure(
      HttpExchange exchange,
      String route,
      Request req,
      Translation.Result result,
      String allowedOrigin)
      throws IOException {
    int status =
        switch (result.failure()) {
          case NONE -> 0;
          case OUT_OF_SCOPE -> 422;
          case SYNTAX_ERROR -> 400;
        };
    if (status == 0) {
      return false;
    }
    LOG.info(
        "POST {} {} {} bytes -> {}", route, req.semanticsName(), req.source().length(), status);
    respond(exchange, status, String.join("\n", result.diagnostics()), allowedOrigin);
    return true;
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
    exchange.getResponseHeaders().set("Access-Control-Expose-Headers", "X-Analysis-Millis");
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
