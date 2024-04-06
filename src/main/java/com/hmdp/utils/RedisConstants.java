package com.hmdp.utils;

/**
 * @author xuyang.li
 * @date 2022/11/26 15:47
 */
public class RedisConstants {

    /**
     * 登录的验证码key
     */
    public static final String LOGIN_CODE_KEY = "login:code:";

    /**
     * 登录验证码的过期时间
     */
    public static final Long LOGIN_CODE_TTL = 30L;

    /**
     * 保存登录信息的key
     */
    public static final String LOGIN_USER_KEY = "login:token:";

    /**
     * 用户登录的过期时间
     */
    public static final Long LOGIN_USER_TTL = 30L;

    /**
     * 缓存空对象的过期时间
     */
    public static final Long CACHE_NULL_TTL = 2L;

    /**
     * 店铺数据的过期时间
     */
    public static final Long CACHE_SHOP_TTL = 30L;
    /**
     * 商铺详细信息缓存
     */
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    /**
     * 首页商铺类型缓存
     */
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop:type";

    /**
     * 互斥锁的key
     */
    public static final String LOCK_SHOP_KEY = "lock:shop:";

    /**
     * 互斥锁失效时间
     */
    public static final Long LOCK_SHOP_TTL = 10L;

    /**
     * 秒杀优惠券库存的key
     */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    /**
     * 博客点赞
     */
    public static final String BLOG_LIKED_KEY = "blog:liked:";

    /**
     * 用户关注
     */
    public static final String FOLLOWS_USER_KEY = "follows:user:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
