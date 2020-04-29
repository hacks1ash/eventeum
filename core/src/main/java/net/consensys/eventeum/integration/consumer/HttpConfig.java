package net.consensys.eventeum.integration.consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class HttpConfig {

    @Bean(name = "cwsRetryTemplate", value = "cwsRetryTemplate")
    public RetryTemplate retryTemplate() {

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(1);

        ExponentialBackOffPolicy exponentialBackOffPolicy = new ExponentialBackOffPolicy();
        exponentialBackOffPolicy.setMultiplier(2D);
        exponentialBackOffPolicy.setInitialInterval(30*1000);
        exponentialBackOffPolicy.setMaxInterval(24*60*60*1000);

        RetryTemplate template = new RetryTemplate();
        template.setBackOffPolicy(exponentialBackOffPolicy);
        template.setRetryPolicy(retryPolicy);

        return template;
    }

}
