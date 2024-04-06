package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author lxy
 * @version 1.0
 * @Description 分布式锁实现
 * @date 2022/12/20 20:49
 */
public class SimpleRedisLock implements ILock{

    private String name;

    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    // RedisScript需要加载unlock.lua文件，为了避免每次释放锁时都加载，我们可以提前加载好。否则每次读取文件就会产生IO，效率很低
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript <>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 线程表示前缀
     */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标示
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().
                setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        // 记得 包装类型到基本类型转换时要注意 空指针问题
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX+Thread.currentThread().getId());
    }

    // @Override
    // public void unlock() {
    //     // 获取当前线程的线程标示
    //     String threadId = ID_PREFIX+Thread.currentThread().getId();
    //     // 获取当前锁的线程标示
    //     String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
    //     // 判断标示是否一致
    //     if(threadId.equals(id)){
    //         // 释放锁
    //         stringRedisTemplate.delete(KEY_PREFIX+name);
    //     }
    // }
}
