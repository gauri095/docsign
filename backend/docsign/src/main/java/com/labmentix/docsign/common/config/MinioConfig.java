package com.labmentix.docsign.common.config;

import com.labmentix.docsign.common.storage.StorageService;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

@Configuration
@Slf4j
public class MinioConfig {

    @Value("${app.minio.endpoint}")
    private String endpoint;

    @Value("${app.minio.access-key}")
    private String accessKey;

    @Value("${app.minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        log.info("Connecting to MinIO at {}", endpoint);
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * Auto-creates the storage bucket on application start if it doesn't exist.
     * Runs after all beans are initialized.
     */
    @EventListener(ContextRefreshedEvent.class)
    public void initBucket(ContextRefreshedEvent event) {
        StorageService storageService = event.getApplicationContext().getBean(StorageService.class);
        storageService.ensureBucketExists();
    }
}