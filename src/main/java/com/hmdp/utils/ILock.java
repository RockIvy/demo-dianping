package com.hmdp.utils;

/**
 * @author lxy
 * @version 1.0
 * @Description 分布式锁接口
 * @date 2022/12/20 20:47
 */
public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间
     * @return true 代表获取锁成功；false代表获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
