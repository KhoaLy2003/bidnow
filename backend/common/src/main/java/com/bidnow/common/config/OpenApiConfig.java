/*
 * BidNow Auction System
 */
package com.bidnow.common.config;

import com.bidnow.common.annotation.AuthenticatedUserId;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "BidNow API",
                version = "1.0",
                description = "Documentation for BidNow Auction System APIs. \n\n" +
                        "Access all microservices through the API Gateway. \n" +
                        "Authentication is handled via JWT Bearer tokens.",
                contact = @Contact(
                        name = "BidNow Support",
                        email = "support@bidnow.com"
                ),
                license = @License(
                        name = "Apache 2.0",
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                )
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer"
)
public class OpenApiConfig {

    static {
        // Tell SpringDoc to ignore controller parameters annotated with @AuthenticatedUserId.
        // These are resolved from the X-User-Id header injected by the API Gateway — they are
        // not part of the public API contract and must not appear in the exported schema.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthenticatedUserId.class);
    }
}
