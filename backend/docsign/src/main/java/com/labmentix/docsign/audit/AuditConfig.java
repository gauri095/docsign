package com.labmentix.docsign.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for the dedicated audit write thread pool.
 *
 * Sizing rationale:
 * - Core 2: low baseline load — audit writes are fast DB inserts
 * - Max 8: headroom for signing storms (all signers submit simultaneously)
 * - Queue 200: absorbs spikes; audit writes must not be dropped
 * - CallerRunsPolicy: if queue is full, the caller thread handles it
 *   (graceful degradation: slightly slower response rather than lost audit)
 */
@Configuration
public class AuditConfig {

    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("docsign-audit-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}