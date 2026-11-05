package project.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** How each Cora outcome maps onto the {@code /v1/analyse} response, without running Cora. */
@WebMvcTest(DemoController.class)
@Import(SimpleMeterRegistry.class)
class DemoControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean CoraRunner cora;

  @BeforeEach
  void stubTimeout() {
    when(cora.timeout()).thenReturn(Duration.ofSeconds(30));
  }

  private void coraAnswers(CoraRunner.Outcome outcome, String output) throws Exception {
    when(cora.analyse(anyString())).thenReturn(new CoraRunner.Analysis(outcome, output, 42));
  }

  private ResultActions analyse(String source) throws Exception {
    return mvc.perform(post("/v1/analyse?semantics=release").content(source));
  }

  @Test
  void returnsCorasVerdict() throws Exception {
    coraAnswers(CoraRunner.Outcome.FINISHED, "YES\nproof");

    analyse(DemoTest.SUM)
        .andExpect(status().isOk())
        .andExpect(content().string("YES\nproof"))
        .andExpect(header().string("X-Analysis-Millis", "42"));
  }

  @Test
  void refusesWithRetryAfterWhenBusy() throws Exception {
    coraAnswers(CoraRunner.Outcome.BUSY, "");

    analyse(DemoTest.SUM)
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "5"));
  }

  @Test
  void answers504WhenCoraTimesOut() throws Exception {
    coraAnswers(CoraRunner.Outcome.TIMED_OUT, "");

    analyse(DemoTest.SUM)
        .andExpect(status().isGatewayTimeout())
        .andExpect(content().string("Cora gave no answer within 30 s"));
  }

  @Test
  void answers502WithCorasOutputWhenItFails() throws Exception {
    coraAnswers(CoraRunner.Outcome.FAILED, "Parsing error");

    analyse(DemoTest.SUM)
        .andExpect(status().isBadGateway())
        .andExpect(content().string("Parsing error"));
  }

  @Test
  void answers503WhenInterrupted() throws Exception {
    when(cora.analyse(anyString())).thenThrow(new InterruptedException());

    analyse(DemoTest.SUM).andExpect(status().isServiceUnavailable());
  }

  @Test
  void neverRunsCoraOnMalformedRust() throws Exception {
    analyse("fn f( {").andExpect(status().isBadRequest());

    verify(cora, never()).analyse(anyString());
  }
}
