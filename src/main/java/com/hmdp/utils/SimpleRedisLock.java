package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.PipedReader;
import java.util.concurrent.TimeUnit;

/**
 * @description: redis锁简单实现
 * @author: Ddalao
 * @create: 2023-10-09 20:46
 **/
public class SimpleRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;
    private String name;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    private static final  String KEY_PREFIX = "lock:";


    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        long id = Thread.currentThread().getId();
        Boolean aBoolean = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, id + "", timeoutSec, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(aBoolean);
    }

    @Override
    public void unlock() {
        //释放锁
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}