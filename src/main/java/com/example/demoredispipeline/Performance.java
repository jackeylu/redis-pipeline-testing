package com.example.demoredispipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.util.Objects;

/**
 * cost 74529243 ns in jedis pipeline
 * cost 4006613513 ns in normal mode
 * cost 3726327129 ns in pipeline
 * cost 252239947 ns in bytes pipeline
 * 最快的是Jedis的pipeline，其次是基于connection的bytes mode pipeline
 *
 * @author lvlin
 * @date 2020-09-25 2:32 PM
 */
@Profile("!test")
@Component
public class Performance implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(Performance.class);
    public static final int NUM_TESTS = 10_000;

    private final StringRedisTemplate stringRedisTemplate;

    public Performance(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("start performance testing");

        jedisPipeline();

        long start = System.nanoTime();
        addLargeRecords(stringRedisTemplate);
        long cost = System.nanoTime() - start;
        logger.info("cost {} ns in normal mode", cost);

        start = System.nanoTime();
        stringRedisTemplate.executePipelined((RedisCallback<String>) redisConnection -> {
            redisConnection.openPipeline();
            logger.info("connection pipeline = {}", redisConnection.isPipelined());
            redisConnection.flushDb();
            logger.info("connection flushDb");

            addLargeRecords(stringRedisTemplate);

            logger.info("closing pipeline");
            redisConnection.closePipeline();
            logger.info("close pipeline");
            return null;
        });
        cost = System.nanoTime() - start;
        logger.info("cost {} ns in pipeline", cost);

        addInBytesMode();
    }

    private void addInBytesMode() {
        long start = System.nanoTime();

        RedisSerializer<String> stringSerializer = stringRedisTemplate.getStringSerializer();
        stringRedisTemplate.executePipelined((RedisCallback<String>) connection -> {
            logger.info("pipeline0 = {}", connection.isPipelined());
            connection.openPipeline();
            logger.info("pipeline1 = {}", connection.isPipelined());

            for (int i = 0; i < NUM_TESTS; i++) {
                String key = "key" + i;
                String value = "vlaue" + i;
                connection.sAdd(Objects.requireNonNull(stringSerializer.serialize(key)),
                        stringSerializer.serialize(value));
            }

            connection.closePipeline();
            return null;
        });
        long cost = System.nanoTime() - start;
        logger.info("cost {} ns in bytes pipeline", cost);
    }

    private void addLargeRecords(StringRedisTemplate stringRedisTemplate) {
        for (int i = 0; i < NUM_TESTS; i++) {
            String key = "key" + i;
            String value = "vlaue" + i;
            stringRedisTemplate.opsForSet().add(key, value);
        }
    }

    private void jedisPipeline() {
        long start = System.nanoTime();
        Jedis jedis = new Jedis();
        Pipeline pipelined = jedis.pipelined();

        for (int i = 0; i < NUM_TESTS; i++) {
            String key = "key" + i;
            String value = "vlaue" + i;
            pipelined.sadd(key, value);
        }

        pipelined.sync();
        pipelined.close();

        long cost = System.nanoTime() - start;
        logger.info("cost {} ns in jedis pipeline", cost);
    }
}
