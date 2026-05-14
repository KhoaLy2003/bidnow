package com.bidnow.media.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponse {
    private UUID id;
    private String name;
    private String type;
    private String language;
    private String subject;
    private String bodyHtml;
    private String bodyText;
    private List<String> variables;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
