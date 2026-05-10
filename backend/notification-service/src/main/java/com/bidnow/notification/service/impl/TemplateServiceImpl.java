package com.bidnow.notification.service.impl;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.specification.SearchOperator;
import com.bidnow.common.specification.SpecificationBuilder;
import com.bidnow.notification.domain.entity.NotificationTemplate;
import com.bidnow.notification.domain.enums.NotificationChannel;
import com.bidnow.notification.domain.enums.NotificationLanguage;
import com.bidnow.notification.dto.request.TemplateRequest;
import com.bidnow.notification.dto.request.criteria.TemplateCriteria;
import com.bidnow.notification.dto.response.TemplateResponse;
import com.bidnow.notification.repository.NotificationTemplateRepository;
import com.bidnow.notification.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final NotificationTemplateRepository templateRepository;

    @Override
    public String processHtmlBody(NotificationTemplate template, Map<String, Object> variables) {
        if (template.getBodyHtml() == null) {
            return null;
        }
        return replaceVariables(template.getBodyHtml(), variables);
    }

    @Override
    public String processTextBody(NotificationTemplate template, Map<String, Object> variables) {
        if (template.getBodyText() == null) {
            return null;
        }
        return replaceVariables(template.getBodyText(), variables);
    }

    @Override
    public String processSubject(NotificationTemplate template, Map<String, Object> variables) {
        if (template.getSubject() == null) {
            return null;
        }
        return replaceVariables(template.getSubject(), variables);
    }

    private String replaceVariables(String text, Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return text;
        }

        Map<String, String> stringVariables = variables.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue() != null ? String.valueOf(e.getValue()) : ""));

        StringSubstitutor substitutor = new StringSubstitutor(stringVariables, "{", "}");
        return substitutor.replace(text);
    }

    @Override
    public TemplateResponse createTemplate(TemplateRequest request) {
        NotificationTemplate template = NotificationTemplate.builder()
                .name(request.getName())
                .type(NotificationChannel.valueOf(request.getType()))
                .language(NotificationLanguage.valueOf(request.getLanguage()))
                .subject(request.getSubject())
                .bodyHtml(request.getBodyHtml())
                .bodyText(request.getBodyText())
                .variables(request.getVariables())
                .active(request.isActive())
                .build();

        return mapToResponse(templateRepository.save(template));
    }

    @Override
    public TemplateResponse updateTemplate(UUID id, TemplateRequest request) {
        NotificationTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Template not found with id: " + id, ErrorCodes.NOT_FOUND));

        template.setName(request.getName());
        template.setType(NotificationChannel.valueOf(request.getType()));
        template.setLanguage(NotificationLanguage.valueOf(request.getLanguage()));
        template.setSubject(request.getSubject());
        template.setBodyHtml(request.getBodyHtml());
        template.setBodyText(request.getBodyText());
        template.setVariables(request.getVariables());
        template.setActive(request.isActive());

        return mapToResponse(templateRepository.save(template));
    }

    @Override
    public TemplateResponse getTemplate(UUID id) {
        NotificationTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Template not found with id: " + id, ErrorCodes.NOT_FOUND));
        return mapToResponse(template);
    }

    @Override
    public Page<TemplateResponse> getTemplates(TemplateCriteria criteria, Pageable pageable) {
        SpecificationBuilder<NotificationTemplate> builder = SpecificationBuilder.forEntity();

        if (criteria.getTypes() != null && !criteria.getTypes().isEmpty()) {
            builder.withIn("type", criteria.getTypes().stream()
                    .map(NotificationChannel::valueOf)
                    .collect(Collectors.toList()));
        }

        if (criteria.getLanguages() != null && !criteria.getLanguages().isEmpty()) {
            builder.withIn("language", criteria.getLanguages().stream()
                    .map(NotificationLanguage::valueOf)
                    .collect(Collectors.toList()));
        }

        builder.withIfPresent("active", SearchOperator.EQUAL, criteria.getActive());

        if (StringUtils.hasText(criteria.getSearch())) {
            String likePattern = "%" + criteria.getSearch().toLowerCase() + "%";
            builder.orGroup(or -> or
                    .withLike("name", likePattern)
                    .withLike("subject", likePattern)
            );
        }

        return templateRepository.findAll(builder.build(), pageable).map(this::mapToResponse);
    }

    private TemplateResponse mapToResponse(NotificationTemplate template) {
        return TemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .type(template.getType().name())
                .language(template.getLanguage().name())
                .subject(template.getSubject())
                .bodyHtml(template.getBodyHtml())
                .bodyText(template.getBodyText())
                .variables(template.getVariables())
                .active(template.isActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
