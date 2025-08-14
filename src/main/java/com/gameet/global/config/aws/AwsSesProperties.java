package com.gameet.global.config.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.ses")
public record AwsSesProperties(
        String accessKey,
        String secretKey,
        String region,
        String fromEmailAddress
) { }
