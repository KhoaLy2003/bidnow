---
name: Infrastructure Story
about: Implement Distributed Tracing for Microservices
title: '[Infra] Implement Distributed Tracing & Log Correlation'
labels: Infrastructure, Story
assignees: ''
---

## User Story
As a **Developer/Operator**, I want to **implement distributed tracing and log correlation** so that I can **track requests across multiple microservices and debug system-wide issues faster using unique Trace IDs**.

## Acceptance Criteria
✅ **Instrumentation:**
- Integrate **Micrometer Tracing** with **OpenTelemetry (OTel)** bridge into the `common` module.
- Enable automatic context propagation across HTTP calls (Feign, RestTemplate/WebClient).
- Configure W3C Trace Context as the default propagation format.

✅ **Log Correlation:**
- Update the centralized logging pattern to include `app_name`, `traceId`, and `spanId`.
- Ensure IDs are automatically populated in the MDC (Mapped Diagnostic Context).
- Support dynamic sampling configuration (default to 100% for development).

✅ **Service Integration:**
- Implement tracing in the `api-gateway` to generate the initial Trace ID for incoming requests.
- Ensure Trace ID consistency when a request travels from Gateway -> Service A -> Service B.

## Technical Notes
- **Stack:** Spring Boot 3.2.4, Java 17, Micrometer Tracing.
- **Dependencies:** `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`.
- **Log Pattern Example:** `%d{yyyy-MM-dd HH:mm:ss.SSS} %5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}] %m%n`.
- **Configuration Path:** Centralized in the `common` module's resources or via Spring Cloud Config.

## Definition of Done
- [ ] Dependencies added and tested in the `common` module.
- [ ] Logging pattern standardized across all services via `common` config.
- [ ] Verified `traceId` and `spanId` appearance in console logs for multi-service requests.
- [ ] Trace IDs successfully propagated through the API Gateway to downstream services.
- [ ] Documentation for local debugging with Trace IDs updated.

## Dependencies
- Link to #1 (Epic)
- #5 (Infra Setup)
