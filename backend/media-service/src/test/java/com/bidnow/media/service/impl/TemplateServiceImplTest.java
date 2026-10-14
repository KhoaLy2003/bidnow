package com.bidnow.media.service.impl;

import com.bidnow.media.domain.entity.NotificationTemplate;
import com.bidnow.media.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TemplateServiceImplTest {

    private final TemplateServiceImpl service = new TemplateServiceImpl(mock(NotificationTemplateRepository.class));

    private static NotificationTemplate template(String subject, String html, String text) {
        return NotificationTemplate.builder().subject(subject).bodyHtml(html).bodyText(text).build();
    }

    @Test
    void htmlBody_escapesVariableValues() {
        NotificationTemplate t = template(null, "<p>{title}</p>", null);

        String out = service.processHtmlBody(t, Map.of("title", "<a href=\"x\">Tom & Jerry</a>"));

        assertThat(out).isEqualTo("<p>&lt;a href=&quot;x&quot;&gt;Tom &amp; Jerry&lt;/a&gt;</p>");
    }

    @Test
    void textBodyAndSubject_doNotEscape() {
        NotificationTemplate t = template("Re: {title}", null, "Hi {title}");
        Map<String, Object> vars = Map.of("title", "<b>\"A\" & B</b>");

        assertThat(service.processTextBody(t, vars)).isEqualTo("Hi <b>\"A\" & B</b>");
        assertThat(service.processSubject(t, vars)).isEqualTo("Re: <b>\"A\" & B</b>");
    }

    @Test
    void valueContainingPlaceholder_isNotExpanded() {
        NotificationTemplate t = template("{title}", "{title}", "{title}");
        Map<String, Object> vars = Map.of("title", "{actionUrl}", "actionUrl", "http://evil");

        assertThat(service.processHtmlBody(t, vars)).isEqualTo("{actionUrl}");
        assertThat(service.processTextBody(t, vars)).isEqualTo("{actionUrl}");
        assertThat(service.processSubject(t, vars)).isEqualTo("{actionUrl}");
    }

    @Test
    void nullValue_rendersEmpty() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("title", null);

        assertThat(service.processHtmlBody(template(null, "a{title}b", null), vars)).isEqualTo("ab");
        assertThat(service.processTextBody(template(null, null, "a{title}b"), vars)).isEqualTo("ab");
    }
}
