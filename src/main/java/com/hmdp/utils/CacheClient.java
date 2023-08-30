package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value , Long time , TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    public void setWithLogicalExpire(String key, Object value , Long time , TimeUnit unit){
        //设置逻辑过期值
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id , Class<R> type, Function<ID, R> dbFallBack, Long time , TimeUnit unit){
        String key = keyPrefix + id;
        //1,redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(json)){
            //存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        //判断是否为空值
        if (json != null) {
            return null;
        }
        //不存在,查询数据库
        R r = dbFallBack.apply(id);

        if(r == null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //写入redis
        this.set(key, r,time,unit);
        return r;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R , ID> R queryWithLogicalExpire(String keyPrefix, ID id , Class<R> type, Function<ID, R> dbFallBack, Long time , TimeUnit unit){
        String key = keyPrefix + id ;
        //从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(json)) {
            //不存在，直接返回
            return null;
        }
        //命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);

        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);

        LocalDateTime expireTime = redisData.getExpireTime();

        //判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
             //未过期
            return r;
        }
        //过期，缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean b = tryLock(lockKey);
        //判断锁是否获取成功
        if(b){

            //成功,开启独立线程缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R apply = dbFallBack.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,apply,time,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }

            });

        }
        //返回过期信息
        return r;

    }


    private  boolean tryLock(String key){
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(aBoolean);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
