package com.bidnow.auction.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds the {@code auction.*} properties: anti-sniping ({@link AntiSnipeProperties}) and closure ({@link ClosureProperties}). */
@Configuration
@EnableConfigurationProperties({AntiSnipeProperties.class, ClosureProperties.class})
public class AuctionPropertiesConfig {
}
