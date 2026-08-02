package com.palavecino.backend.config;

import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} and provides the executor used for email sending. Emails run off the
 * request thread so a slow or unreachable SMTP server never blocks (or fails) the flow that
 * triggered them.
 *
 * <p>{@code app.mail.async=false} swaps in a synchronous executor — used by the test suite so
 * the fake {@code EmailSender} is populated before the HTTP call returns, keeping assertions
 * deterministic.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "mailTaskExecutor")
    @ConditionalOnProperty(name = "app.mail.async", havingValue = "true", matchIfMissing = true)
    public Executor asyncMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "mailTaskExecutor")
    @ConditionalOnProperty(name = "app.mail.async", havingValue = "false")
    public Executor synchronousMailExecutor() {
        return Runnable::run;
    }
}
