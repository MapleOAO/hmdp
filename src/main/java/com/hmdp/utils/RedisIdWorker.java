package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.xml.crypto.Data;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    /*
    * 开始时间戳
    * */
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /*
    * 序列号位数
    * */
    private static final int COUNT_BITS = 32;


    public long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
        //2.生成序列号
        //获取当前日期精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //3.拼接返回
        return timeStamp << COUNT_BITS | count;
    }
}
