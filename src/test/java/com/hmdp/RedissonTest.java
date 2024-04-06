package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @author lxy
 * @version 1.0
 * @Description 测试Redisson的分布式锁的可重入性质
 * @date 2022/12/21 16:01
 */
@Slf4j
@SpringBootTest
public class RedissonTest {

    @Resource
    private RedissonClient redissonClient;

    // @Resource
    // private RedissonClient redissonClient2;
    //
    // @Resource
    // private RedissonClient redissonClient3;

    private RLock lock;

    @BeforeEach
    void setUp(){
        // RLock lock1 = redissonClient.getLock("order");
        // RLock lock2 = redissonClient2.getLock("order");
        // RLock lock3 = redissonClient3.getLock("order");

        // 创建联锁 multiLock （这里其实用哪个去掉方法都可以，
        // 通过观察源码发现，底层是new RedissonMultiLock(lock1，lock2,lick3)）
        // lock = redissonClient.getMultiLock(lock1, lock2, lock3);
        lock = redissonClient.getLock("order");;
    }

    @Test
    void method1() throws InterruptedException {
        // 尝试获取锁
        boolean isLock = lock.tryLock(1L, TimeUnit.MINUTES);
        if (!isLock){
            log.error("获取锁失败....1");
            return;
        }
        try {
            log.info("获取锁成功....1");
            method2();
            log.info("开始执行业务....1");
        } finally {
            log.warn("开始释放锁....1");
            lock.unlock();
        }

    }

    void method2() throws InterruptedException {
        // 尝试获取锁
        // boolean isLock = lock.tryLock();
        boolean isLock = lock.tryLock(1,10000L,TimeUnit.SECONDS);
        if(!isLock){
            log.error("获取锁失败....2");
            return;
        }
        try {
            log.info("获取锁成功....2");
            log.info("开始执行业务....2");
        } finally {
            log.warn("准备释放锁....2");
            lock.unlock();
        }
    }
}
