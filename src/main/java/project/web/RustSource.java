package project.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * The Rust source sent as a request body: the raw bytes as UTF-8, whatever the {@code
 * Content-Type}, and at most {@link #MAX_BYTES}.
 *
 * <p>A handler must declare it <em>before</em> any {@code @RequestParam}. For a form-encoded {@code
 * POST} (what {@code curl --data} sends) the servlet container parses the body as form parameters
 * on the first parameter lookup, consuming it; reading the body first stops that.
 *
 * @param text the source text
 */
record RustSource(String text) {

  static final int MAX_BYTES = 64 * 1024;

  /** Resolves {@link RustSource} handler parameters; registered by {@link DemoConfiguration}. */
  static final class Resolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
      return parameter.getParameterType() == RustSource.class;
    }

    @Override
    public RustSource resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory)
        throws IOException {
      return new RustSource(read(webRequest.getNativeRequest(HttpServletRequest.class)));
    }

    /**
     * Refuses anything over {@link #MAX_BYTES} with {@code 413}: up front when {@code
     * Content-Length} says so, otherwise once that many bytes have arrived.
     */
    private static String read(HttpServletRequest request) throws IOException {
      if (request.getContentLengthLong() > MAX_BYTES) {
        throw tooLarge();
      }
      try (InputStream in = request.getInputStream();
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) != -1) {
          total += read;
          if (total > MAX_BYTES) {
            throw tooLarge();
          }
          out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8);
      }
    }

    private static RefusedRequestException tooLarge() {
      return new RefusedRequestException(
          HttpStatus.CONTENT_TOO_LARGE, "request body exceeds 64 KiB");
    }
  }
}
