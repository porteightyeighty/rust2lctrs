package project.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The HTTP API in front of {@link project.Translation} for the web demo, as a Spring Boot
 * application. It ships as its own jar ({@code rust2lctrs-<version>-server.jar}, started with
 * {@code java -jar}); the CLI jar leaves out this package and Spring.
 *
 * <p>{@code /v1/translate} returns the LCTRS. {@code /v1/analyse} translates and then runs Cora on
 * the result (see {@link CoraRunner}); it answers {@code 503} when the server has no Cora install.
 * The routes are in {@link DemoController}, the error contract in {@link DemoErrors}, and the
 * configuration (mapped from the {@code PORT}, {@code ALLOWED_ORIGIN} and {@code CORA_*}
 * environment variables) in {@code application.properties}.
 */
@SpringBootApplication
public class Demo {

  public static void main(String[] args) {
    SpringApplication.run(Demo.class, args);
  }
}
