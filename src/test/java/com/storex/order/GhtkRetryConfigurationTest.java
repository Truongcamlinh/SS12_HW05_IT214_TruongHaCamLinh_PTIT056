package com.storex.order;

import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GhtkRetryConfigurationTest {
    @Autowired
    private RetryRegistry registry;

    @Test
    void loadsRequiredRetryConfiguration() {
        var config = registry.retry("ghtkClient").getRetryConfig();

        assertThat(config.getMaxAttempts()).isEqualTo(3);
        assertThat(config.getIntervalBiFunction().apply(1, null)).isEqualTo(2_000L);
        assertThat(config.getExceptionPredicate().test(new TimeoutException())).isTrue();
        assertThat(config.getExceptionPredicate().test(new IllegalArgumentException())).isFalse();
    }
}

