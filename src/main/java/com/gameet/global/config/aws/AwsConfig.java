package com.gameet.global.config.aws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@RequiredArgsConstructor
public class AwsConfig {

    private final AwsSesProperties awsSesProperties;

    @Bean
    public SesV2Client sesV2Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                awsSesProperties.accessKey(),
                awsSesProperties.secretKey()
        );

        return SesV2Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(awsSesProperties.region()))
                .build();
    }
}