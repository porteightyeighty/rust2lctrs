package project.web;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for {@link CoraRunner}, bound from {@code cora.*}. {@code application.properties}
 * maps the deployment's environment variables onto these, so {@code CORA_BIN}, {@code
 * CORA_TIMEOUT_SECONDS}, {@code CORA_CONCURRENCY} and {@code CORA_JAVA_OPTS} keep working.
 *
 * @param bin a path to the Cora executable, or a command name to look up on {@code PATH}
 * @param timeout how long one analysis may run
 * @param concurrency how many analyses may run at once
 * @param javaOpts passed to Cora as {@code JAVA_OPTS}, for example to cap its heap
 */
@ConfigurationProperties("cora")
public record CoraProperties(
    @DefaultValue("cora") String bin,
    @DefaultValue("30s") Duration timeout,
    @DefaultValue("2") int concurrency,
    @DefaultValue("-Xmx384m") String javaOpts) {}
