package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @description: redisson配置类
 * @author: Ddalao
 * @create: 2023-10-12 20:49
 **/
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://8.130.49.172:6379").setPassword("123456");
        return Redisson.create(config);
    }
}