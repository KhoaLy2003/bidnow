package com.bidnow.common.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import net.logstash.logback.argument.StructuredArgument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private ListAppender<ILoggingEvent> appender;
    private Logger filterLogger;

    @BeforeEach
    void setUp() {
        filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
        filterLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(appender);
    }

    @Test
    void logsSuccessfulRequestAtInfo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auctions/123");
        request.addHeader("X-User-Id", "user-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("GET")
                .contains("/api/v1/auctions/123")
                .contains("200")
                .contains("userId=user-42");
    }

    @Test
    void logsClientErrorAtWarn() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auctions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(404);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("404").contains("userId=-");
    }

    @Test
    void logsServerErrorAtError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auctions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("500");
    }

    @Test
    void logsExceptionDetailsWhenChainThrowsUncaught() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auctions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new RuntimeException("boom");
        };

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, chain));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
    }

    @Test
    void logsFieldsAsStructuredArgumentsForJsonAggregation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auctions/123");
        request.addHeader("X-User-Id", "user-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Map<String, Object> fields = structuredFields(onlyEvent().getArgumentArray());

        assertThat(fields)
                .containsEntry("method", "GET")
                .containsEntry("path", "/api/v1/auctions/123")
                .containsEntry("status", 200)
                .containsEntry("userId", "user-42");
        assertThat(fields.get("elapsedMs")).isInstanceOf(Number.class);
    }

    private static Map<String, Object> structuredFields(Object[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> merged = new LinkedHashMap<>();
        for (Object arg : args) {
            assertThat(arg)
                    .as("log argument should be a StructuredArgument so the JSON encoder can extract a field")
                    .isInstanceOf(StructuredArgument.class);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JsonGenerator generator = new JsonFactory().createGenerator(out);
            generator.writeStartObject();
            ((StructuredArgument) arg).writeTo(generator);
            generator.writeEndObject();
            generator.close();
            merged.putAll(mapper.readValue(out.toByteArray(), Map.class));
        }
        return merged;
    }

    @Test
    void skipsActuatorPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(appender.list).isEmpty();
    }

    private ILoggingEvent onlyEvent() {
        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        return events.get(0);
    }
}
