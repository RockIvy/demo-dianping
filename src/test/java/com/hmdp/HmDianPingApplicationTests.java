package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.hmdp.config.ResourceConfig;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SendSmsUtil;
import lombok.Cleanup;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ResourceConfig config;

    @Resource
    IShopService shopService;

    @Resource
    CacheClient cacheClient;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    IUserService userService;

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void testSendSms(){
        String[] phone = new String[1];
        String[] templateParam = new String[2];
        phone[0] = "18625983574";
        templateParam[0] = "123456";
        templateParam[1] = "5";
        SendSmsUtil.sendSms(phone,templateParam,config);
    }

    @Test
    public void getResourceConfig(){
        System.out.println(config.getSmsSecretId());
        System.out.println(config.getSmsSecretKey());
        System.out.println(config.getSmsSdkAppId());
        System.out.println(config.getSmsSignName());
        System.out.println(config.getSmsTemplateId());
    }

    /**
     * 预热
     */
    @Test
    public void testSave2Redis() throws InterruptedException {
        shopService.saveShop2Redis(1L,30L);
    }

    @Test
    public void testCachePreHotWithMutex(){
        Shop shop = shopService.getById(1);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+1, JSONUtil.toJsonStr(shop),20,TimeUnit.SECONDS);
    }

    @Test
    public void testCachePreHotWithLogicalExpire(){
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+1,shopService.getById(1),20L,TimeUnit.SECONDS);
    }

    @Test
    public void testIdWorker() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);

        Runnable task = ()->{
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = "+id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = "+(end - begin));
    }

    @Test
    public void test(){
        Voucher voucher = new Voucher();
        voucher.setShopId(1L);
        voucher.setTitle("100元优惠券");
        voucher.setRules("全场通用 \\n无需预约 \\n可无限叠加 \\不兑现、不找零 \\n仅限食堂");
        voucher.setPayValue(8000L);
        voucher.setActualValue(10000L);
        voucher.setType(1);
        voucher.setStock(100);
        voucher.setBeginTime(LocalDateTime.of(2022,12,12,13,0,0));
        voucher.setEndTime(LocalDateTime.of(2022,12,23,23,59,59));
        System.out.println(JSONUtil.toJsonStr(voucher));
    }

    /**
     * 在Redis中保存1000个用户信息并将其token写入文件中，方便测试多人秒杀业务
     */
    @Test
    void testMultiLogin() throws IOException {
        List <User> userList = userService.lambdaQuery().last("limit 1000").list();
        for (User user : userList) {
            String token = UUID.randomUUID().toString(true);
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map <String,Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap <>(),
                    CopyOptions.create().ignoreNullValue()
                            .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
            String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        }
        Set <String> keys = stringRedisTemplate.keys(RedisConstants.LOGIN_USER_KEY + "*");
        @Cleanup FileWriter fileWriter = new FileWriter(System.getProperty("user.dir") + "\\tokens.txt");
        @Cleanup BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
        assert keys != null;
        for (String key : keys) {
            String token = key.substring(RedisConstants.LOGIN_USER_KEY.length());
            String text = token + "\n";
            bufferedWriter.write(text);
        }
    }

    @Test
    public void testResult(){
        Result ok = Result.ok();
        System.out.println( JSONUtil.toJsonStr(ok));
    }
}
