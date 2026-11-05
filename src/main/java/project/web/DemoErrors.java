package project.web;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The demo's error contract: every refusal is a {@code text/plain} body with a short explanation
 * (or the translator's diagnostics), which the web front end shows as is.
 */
@RestControllerAdvice
class DemoErrors {

  private static final Logger LOG = LoggerFactory.getLogger(DemoErrors.class);

  @ExceptionHandler(RefusedRequestException.class)
  ResponseEntity<String> refused(RefusedRequestException e) {
    LOG.info("refused -> {}", e.status().value());
    return DemoController.text(e.status(), e.getMessage());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<String> wrongMethod(HttpRequestMethodNotSupportedException e) {
    ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
    Set<HttpMethod> supported = e.getSupportedHttpMethods();
    if (supported != null) {
      response.allow(supported.toArray(HttpMethod[]::new));
    }
    return response.contentType(DemoController.TEXT).body("method not allowed");
  }
}
