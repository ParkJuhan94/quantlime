package com.quantlime.event.observability;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KafkaDltProperties.class)
public class KafkaDltConfig {
}
