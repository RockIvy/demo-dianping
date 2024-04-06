package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author lxy
 * @version 1.0
 * @Description Redis操作缓存的工具类
 * @date 2022/12/6 0:33
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    /**
     *  逻辑过期解决缓存击穿问题中的缓存重建
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key,Object value,Long time,TimeUnit unit){
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 根据指定的key查询缓存,并利用缓存空值来解决缓存穿透问题
     * @param keyPrefix key前缀
     * @param id
     * @param type
     * @param dbFallback 降级的函数
     * @param time       时间
     * @param unit       单位
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function <ID,R> dbFallback,Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从Redis中查询R数据
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.存在,则直接返回
            return JSONUtil.toBean(json, type);
        }

        // 4.判断命中的是否是空值 (上面已经判断过不为空的情况了，下面只有 “” 和 null的两种情况，为null说明不存在，为“”说明空缓存)
        if (json != null) {
            return null;
        }

        // 3.如果没有，就去查数据库
        R r = dbFallback.apply(id);
        // 4.如果没找到则返回错误信息
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 5.如果查到了就加入到Redis,并返回
        this.set(key,r,time,unit);
        return r;
    }

    private ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据指定的key查询缓存，并利用逻辑过期时间来解决缓存击穿问题
     *
     * @param id
     * @return
     */
    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type
    ,Function <ID,R> dbFallback,Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从Redis中查询商铺缓存
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否命中
        if (StrUtil.isBlank(redisDataJson)) {
            // 3.未命中，则返回空(因为预热过了，所以如果缓存中没有，则一定就是没有该店铺数据)
            return null;
        }
        // 4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        JSONObject jsonObj = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(jsonObj, type);
        // 5.判断是否过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            // 5.1未过期，则返回商铺信息
            return r;
        }
        // 5.2已过期，需要缓存重建
        // 6.缓存重建
        // 6.1尝试获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2判断互斥锁是否获取成功
        if (isLock) {
            // 6.3获取成功，则开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R newR = dbFallback.apply(id);
                    // 进行缓存重建
                    this.setWithLogicalExpire(key,newR,time,unit);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 6.6释放锁
                    unlock(lockKey);
                }
            });

        }
        // 7.获取互斥锁失败，则直接返回过期的R数据
        return r;
    }

    /**
     * 根据指定的key查询缓存，并利用互斥锁解决缓存击穿解决缓存击穿问题
     *
     * @param id
     * @return
     */
    public <R,ID> R queryWithMutex(String keyPrefix,ID id,Class<R> type,
                                    Function <ID,R> dbFallback,Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从查询Redis中是否有数据
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 存在则直接返回
            R r = JSONUtil.toBean(json, type);
            return r;
        }

        // 3.判断命中的是否是 “” (缓存空值)
        if (json != null) {
            return null;
        }

        // 4.实现缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        R r = null;
        try {
            // 4.1 获取互斥锁
            boolean isLock = tryLock(lockKey);
            // 4.2判断是否获取成功
            if (!isLock) {
                // 4.3失败，则休眠并重试
                Thread.sleep(50);
                // 注意：获取锁的同时应该再次检测redis缓存是否存在，做DoubleCheck,如果存在则无需重建缓存
                return queryWithMutex(keyPrefix,id,type,dbFallback,time,unit);
            }
            // 4.4成功，根据id查询数据库
            r = dbFallback.apply(id);

            // 模拟重建时的延时
            Thread.sleep(200);

            // 5.不存在，返回错误
            if (r == null) {
                // 将空值写入到redis
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 6.存在就加入到Redis,并返回
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r),time, unit);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 7.释放互斥锁
            unlock(lockKey);
        }
        return r;
    }

    /**
     * 获取锁：使用setnx模拟互斥锁
     * 为了防止出现死锁，所以应该为其设置过期时间
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
