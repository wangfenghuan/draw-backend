package com.wfh.drawio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * @Title: MinioClientConfig
 * @Author wangfenghuan
 * @Package com.wfh.drawio.config
 * @Date 2025/12/22 18:59
 * @description:
 */
@Configuration
@ConfigurationProperties(prefix = "rustfs.client")
@Data
public class RustFsClientConfig {

    private String endpoint;
    private String accessKey;
    private String acessSecret;
    private String bucketName;


    @Bean
    public S3Client s3Client(){
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, acessSecret)
                ))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, acessSecret)))
                .endpointOverride(URI.create(endpoint))
                .build();
    }

}
