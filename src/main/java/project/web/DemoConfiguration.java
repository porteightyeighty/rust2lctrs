package project.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires the demo's {@link CoraRunner}, its CORS policy and the {@link RustSource} body reader. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CoraProperties.class)
class DemoConfiguration implements WebMvcConfigurer {

  private static final Logger LOG = LoggerFactory.getLogger(DemoConfiguration.class);

  private final String allowedOrigin;

  DemoConfiguration(@Value("${demo.allowed-origin:*}") String allowedOrigin) {
    this.allowedOrigin = allowedOrigin;
  }

  /**
   * The runner behind {@code /v1/analyse}. Returns {@code null}, so that no bean is registered,
   * when Cora cannot be found; {@link DemoController} then answers that route with {@code 503}.
   */
  @Bean
  CoraRunner coraRunner(CoraProperties properties) {
    Optional<CoraRunner> runner =
        CoraRunner.resolve(properties.bin(), System.getenv("PATH"))
            .map(
                bin ->
                    new CoraRunner(
                        bin,
                        properties.timeout(),
                        properties.concurrency(),
                        Map.of("JAVA_OPTS", properties.javaOpts())));
    if (runner.isEmpty()) {
      LOG.warn("Cora not found (set CORA_BIN): /v1/analyse will answer 503");
    }
    return runner.orElse(null);
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new RustSource.Resolver());
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/v1/**")
        .allowedOrigins(allowedOrigin)
        .allowedMethods("POST", "OPTIONS")
        .allowedHeaders("Content-Type")
        .exposedHeaders("X-Analysis-Millis", "Retry-After");
    registry.addMapping("/health").allowedOrigins(allowedOrigin).allowedMethods("GET");
  }
}
