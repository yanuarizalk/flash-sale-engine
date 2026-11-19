package com.flashsale.engine.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient(RedisConnectionDetails connectionDetails) {
        String host = connectionDetails.getStandalone() != null ? connectionDetails.getStandalone().getHost() : "localhost";
        int port = connectionDetails.getStandalone() != null ? connectionDetails.getStandalone().getPort() : 6379;

        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(host)
                .withPort(port);

        if (connectionDetails.getPassword() != null && !connectionDetails.getPassword().isBlank()) {
            uriBuilder.withPassword(connectionDetails.getPassword().toCharArray());
        }

        return RedisClient.create(uriBuilder.build());
    }

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisConnectionDetails connectionDetails) {
        String host = connectionDetails.getStandalone() != null ? connectionDetails.getStandalone().getHost() : "localhost";
        int port = connectionDetails.getStandalone() != null ? connectionDetails.getStandalone().getPort() : 6379;

        Config config = new Config();
        var singleServerConfig = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(50)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        if (connectionDetails.getPassword() != null && !connectionDetails.getPassword().isBlank()) {
            singleServerConfig.setPassword(connectionDetails.getPassword());
        }

        return Redisson.create(config);
    }

    @Bean
    public ProxyManager<String> bucket4jProxyManager(RedisClient redisClient) {
        LettuceBasedProxyManager<byte[]> byteProxyManager = LettuceBasedProxyManager.builderFor(redisClient)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(60)))
                .build();

        return byteProxyManager.withMapper(key -> key.getBytes(StandardCharsets.UTF_8));
    }
}
