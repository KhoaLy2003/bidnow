package com.bidnow.auction.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds the {@code auction.*} properties: anti-sniping, closure and ending-soon alerts. */
@Configuration
@EnableConfigurationProperties({AntiSnipeProperties.class, ClosureProperties.class, EndingSoonProperties.class})
public class AuctionPropertiesConfig {
}
