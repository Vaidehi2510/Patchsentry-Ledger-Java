package com.patchsentry.ledger.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patchsentry.ledger.core.RunReportValidator;
import com.patchsentry.ledger.core.ScoringPolicies;
import com.patchsentry.ledger.core.ScoringPolicy;
import com.patchsentry.ledger.persistence.RunnerTokenRepository;
import com.patchsentry.ledger.ratelimit.RateLimiter;
import com.patchsentry.ledger.ratelimit.RedisRateLimiter;
import com.patchsentry.ledger.web.AdminAuthFilter;
import com.patchsentry.ledger.web.ProblemResponses;
import com.patchsentry.ledger.web.RunnerAuthFilter;
import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class LedgerConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ScoringPolicy scoringPolicy(LedgerProperties properties) {
        return ScoringPolicies.byName(properties.scoringPolicy());
    }

    @Bean
    RunReportValidator runReportValidator() {
        return new RunReportValidator();
    }

    @Bean
    RateLimiter rateLimiter(StringRedisTemplate redis, LedgerProperties properties, Clock clock) {
        return new RedisRateLimiter(redis, properties.rateLimitPerMinute(), clock);
    }

    @Bean
    ProblemResponses problemResponses(ObjectMapper objectMapper) {
        return new ProblemResponses(objectMapper);
    }

    // Filters are registered explicitly (not as @Components) so each one runs only on its own path.
    @Bean
    FilterRegistrationBean<AdminAuthFilter> adminAuthFilter(LedgerProperties properties, ProblemResponses problems) {
        FilterRegistrationBean<AdminAuthFilter> registration =
                new FilterRegistrationBean<>(new AdminAuthFilter(properties, problems));
        registration.addUrlPatterns("/api/admin/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RunnerAuthFilter> runnerAuthFilter(
            RunnerTokenRepository tokens, RateLimiter rateLimiter, ProblemResponses problems) {
        FilterRegistrationBean<RunnerAuthFilter> registration =
                new FilterRegistrationBean<>(new RunnerAuthFilter(tokens, rateLimiter, problems));
        registration.addUrlPatterns("/api/runner/*");
        registration.setOrder(2);
        return registration;
    }
}
