package project.web;

import org.springframework.http.HttpStatus;

/**
 * A request the demo refuses. {@link DemoErrors} turns it into a {@code text/plain} response with
 * this status and the message as the body.
 */
final class RefusedRequestException extends RuntimeException {

  private final HttpStatus status;

  RefusedRequestException(HttpStatus status, String message) {
    super(message, null, false, false);
    this.status = status;
  }

  HttpStatus status() {
    return status;
  }
}
