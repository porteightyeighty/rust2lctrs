package project.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.Translation;
import project.translator.IntegerSemantics;

/**
 * The demo's routes. Both {@code POST} routes take Rust source as the raw request body (at most
 * {@link RustSource#MAX_BYTES} bytes) and answer {@code text/plain}; refusals go through {@link
 * DemoErrors}.
 */
@RestController
class DemoController {

  private static final Logger LOG = LoggerFactory.getLogger(DemoController.class);

  static final MediaType TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);

  private final Optional<CoraRunner> cora;
  private final MeterRegistry meters;

  DemoController(Optional<CoraRunner> cora, MeterRegistry meters) {
    this.cora = cora;
    this.meters = meters;
  }

  /** Kept alongside {@code /actuator/health} for the existing deployment's health check. */
  @GetMapping("/health")
  ResponseEntity<String> health() {
    return text(HttpStatus.OK, "ok");
  }

  @PostMapping("/v1/translate")
  ResponseEntity<String> translate(
      RustSource rust, // first: see RustSource
      @RequestParam(name = "semantics", defaultValue = "debug") String semanticsName,
      @RequestParam(name = "raw", required = false) String raw) {
    IntegerSemantics semantics = parseSemantics(semanticsName);
    String source = rust.text();
    Translation.Result result = Translation.of(source, semantics, "1".equals(raw), "<http>");
    refuseFailure(result);
    LOG.info("POST /v1/translate {} {} bytes -> 200", semanticsName, source.length());
    return text(HttpStatus.OK, result.lctrs());
  }

  @PostMapping("/v1/analyse")
  ResponseEntity<String> analyse(
      RustSource rust, // first: see RustSource
      @RequestParam(name = "semantics", defaultValue = "debug") String semanticsName) {
    IntegerSemantics semantics = parseSemantics(semanticsName);
    String source = rust.text();
    if (cora.isEmpty()) {
      throw new RefusedRequestException(
          HttpStatus.SERVICE_UNAVAILABLE, "termination analysis is not available on this server");
    }
    // Always analyse the simplified system: simplification preserves termination, and it is the
    // form the benchmarks are checked in.
    Translation.Result result = Translation.of(source, semantics, false, "<http>");
    refuseFailure(result);

    CoraRunner.Analysis analysis;
    try {
      analysis = cora.get().analyse(result.lctrs());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RefusedRequestException(
          HttpStatus.SERVICE_UNAVAILABLE, "the server is shutting down");
    }
    Timer.builder("demo.analysis")
        .description("Cora runs behind /v1/analyse, by outcome")
        .tag("outcome", analysis.outcome().name().toLowerCase(Locale.ROOT))
        .register(meters)
        .record(Duration.ofMillis(analysis.millis()));

    HttpStatus status =
        switch (analysis.outcome()) {
          case FINISHED -> HttpStatus.OK;
          case BUSY -> HttpStatus.SERVICE_UNAVAILABLE;
          case TIMED_OUT -> HttpStatus.GATEWAY_TIMEOUT;
          case FAILED -> HttpStatus.BAD_GATEWAY;
        };
    String body =
        switch (analysis.outcome()) {
          case FINISHED, FAILED -> analysis.output();
          case BUSY -> "the analyser is busy; try again in a few seconds";
          case TIMED_OUT -> "Cora gave no answer within " + cora.get().timeout().toSeconds() + " s";
        };
    LOG.info(
        "POST /v1/analyse {} {} bytes -> {} in {} ms",
        semanticsName,
        source.length(),
        status.value(),
        analysis.millis());
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(status)
            .contentType(TEXT)
            .header("X-Analysis-Millis", Long.toString(analysis.millis()));
    if (analysis.outcome() == CoraRunner.Outcome.BUSY) {
      response.header("Retry-After", "5");
    }
    return response.body(body);
  }

  static ResponseEntity<String> text(HttpStatus status, String body) {
    return ResponseEntity.status(status).contentType(TEXT).body(body);
  }

  private static IntegerSemantics parseSemantics(String name) {
    return switch (name) {
      case "debug" -> IntegerSemantics.debug;
      case "release" -> IntegerSemantics.release;
      case "unbounded" -> IntegerSemantics.unbounded;
      default ->
          throw new RefusedRequestException(HttpStatus.BAD_REQUEST, "unknown semantics: " + name);
    };
  }

  /** Refuses a failed translation with {@code 422} (out of scope) or {@code 400} (syntax error). */
  private static void refuseFailure(Translation.Result result) {
    HttpStatus status =
        switch (result.failure()) {
          case NONE -> null;
          case OUT_OF_SCOPE -> HttpStatus.UNPROCESSABLE_CONTENT;
          case SYNTAX_ERROR -> HttpStatus.BAD_REQUEST;
        };
    if (status != null) {
      throw new RefusedRequestException(status, String.join("\n", result.diagnostics()));
    }
  }
}
