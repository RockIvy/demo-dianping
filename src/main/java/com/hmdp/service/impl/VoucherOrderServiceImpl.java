package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl <VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript <Long> SECKILL_SCRIPT;

    /**
     * 将代理对象声明成全局
     */
    private IVoucherOrderService proxy;

    /**
     * 存放任务的阻塞队列
     * 特点：当一个线程尝试从队列中获取元素，没有元素，线程就会被阻塞，直到队列中有元素，线程才会被唤醒，并去获取元素
     */
    private BlockingQueue <VoucherOrder> orderTasks = new ArrayBlockingQueue <>(1024 * 1024);

    /**
     * 思考一个问题：为什么要使用线程池呢，而不是直接创建一个线程？
     * 其实直接创建一个线程也行，但是创建一个线程开销很大的，用阻塞队列+线程池的形式实现了线程的的复用
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * 由于用户秒杀的时间可能是随时的，所以需要我们项目已启动 线程池就应该从消息队列获取任务，然后工作...
     *
     * @PostConstruct类初始花后立刻执行
     */
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     * 优惠券订单处理器【基于消息队列】
     */
    private class VoucherOrderHandler implements Runnable {

        private final static String queueName = "stream.orders";

        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息  XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List <MapRecord <String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2.判断消息是否获取成功
                    if (list == null || list.isEmpty()) {
                        // 如果获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 3.解析消息中的订单信息
                    MapRecord <String, Object, Object> record = list.get(0);
                    Map <Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(),true);
                    // 4.如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    // 5.ACK确认 SACK strea.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常：", e);
                    handlePendingList();
                }
            }
        }

        /**
         * 处理PendingList中的订单
         */
        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息  XREADGROUP GROUP g1 c1 COUNT 1  STREAMS stream.orders 0
                    List <MapRecord <String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 2.判断消息是否获取成功
                    if (list == null || list.isEmpty()) {
                        // 如果获取失败，说明pending-list没有异常消息，结束循环
                        break;
                    }
                    // 3.解析消息中的订单信息
                    MapRecord <String, Object, Object> record = list.get(0);
                    Map <Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(),true);
                    // 4.如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    // 5.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理pending-list订单异常：", e);
                    try {
                        // 如果出现异常,休眠一会再尝试,避免一直尝试一直异常~
                        Thread.sleep(20);
                    } catch (InterruptedException interruptedException) {
                        interruptedException.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 优惠券订单处理器【基于阻塞队列】
     */
    // private class VoucherOrderHandler implements Runnable {
    //
    //     @Override
    //     public void run() {
    //         while (true) {
    //             try {
    //                 // 1.获取队列中的订单信息
    //                 // take()：获取和删除该队列的头部，如果没有则阻塞等待，直到有元素可用。所以使用该方法，如果有元素，线程就工作，没有线程就阻塞（卡）在这里，不用担心CPU会空转~
    //                 VoucherOrder voucherOrder = orderTasks.take();
    //                 // 2.创建订单
    //                 handleVoucherOrder(voucherOrder);
    //             } catch (Exception e) {
    //                 log.error("处理订单异常：", e);
    //             }
    //         }
    //     }
    // }

    /**
     * 创建订单
     *
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {

        /**
         * 其实这里可以不加锁了:（方式一）
         * ①:前面的Lua脚本已经进判断过库存和一人一单了，并且也可以保证执行的原子性（一次只有一个线程执行）。
         * ②:此时线程池中只有一个线程,是单线程哦~
         * ③:之后从消息队列取任务执行并不需要保证其原子性，因为就不存在并发安全问题了
         * 加锁算是一种兜底~
         */

        // 方式一：加分布式锁再创建订单
        // // 1.获取用户
        // // 注意：这里userId不能从UserHolder中去取，因为当前并不是主线程，而是子线程，无法拿到父线程ThreadLocal中的数据
        // Long userId = voucherOrder.getUserId();
        // // 2.获取分布式锁
        // RLock lock = redissonClient.getLock("lock:order:" + userId);
        // boolean isLock = lock.tryLock();
        // // 3.判断是否获取锁成功
        // if (!isLock) {
        //     // 获取锁失败，返回错误和重试
        //     log.error("不允许重复下单~");
        // }
        // try {
        //     // 获取代理对象（只有通过代理对象调用方法，事务才会生效）
        //     // 注意：这里直接通过以下方式获取肯定是不行的。因为方法底层也是基于ThreadLocal获取的，子线程是无法获取父线程ThreadLocal中的对象的
        //     // 解决办法：在seckillVoucher中提前获取，然后通过消息队列传入或者声明成全局变量，从而就可以使用了
        //     // IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        //     proxy.createVoucherOrder(voucherOrder.getVoucherId());
        // } finally {
        //     lock.unlock();
        // }

        // 方式二：直接创建订单
        proxy.createVoucherOrder(voucherOrder);
    }

    // RedisScript需要加载seckill.lua文件，为了避免每次释放锁时都加载，我们可以提前加载好。否则每次读取文件就会产生IO，效率很低
    static {
        SECKILL_SCRIPT = new DefaultRedisScript <>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 使用Lua脚本 + Stream消息队列实现秒杀下单
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 获取订单id
        long orderId = redisIdWorker.nextId("order");
        // 1.执行Lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );

        // 2.判断结果是否为0
        if (result != 0) {
            // 2.1 不为0，代表没有购买资格
            return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2 为0，有购买资格，把下单信息保存到消息队列【已经在LUA做过了】

        // 3.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        // 4. 返回订单id
        return Result.ok(orderId);
    }

    // /**
    //  * 使用Lua脚本+阻塞队列实现秒杀下单
    //  *
    //  * @param voucherId
    //  * @return
    //  */
    // @Override
    // public Result seckillVoucher(Long voucherId) {
    //     // 获取用户id
    //     Long userId = UserHolder.getUser().getId();
    //     // 1.执行Lua脚本
    //     Long result = stringRedisTemplate.execute(
    //             SECKILL_SCRIPT,
    //             Collections.emptyList(),
    //             voucherId.toString(),
    //             userId.toString()
    //     );
    //
    //     // 2.判断结果是否为0
    //     if (result != 0) {
    //         // 2.1 不为0，代表没有购买资格
    //         return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
    //     }
    //     // 2.2 为0，有购买资格，把下单信息保存到消息队列
    //     // 2.3 创建订单
    //     VoucherOrder voucherOrder = new VoucherOrder();
    //     // 2.4 订单id
    //     long orderId = redisIdWorker.nextId("order");
    //     voucherOrder.setId(orderId);
    //     // 2.5 用户id
    //     voucherOrder.setUserId(userId);
    //     // 2.6代金券id
    //     voucherOrder.setVoucherId(voucherId);
    //     // 2.7放入阻塞队列【理论上只要放入消息队列就有购买资格】
    //     orderTasks.add(voucherOrder);
    //
    //     // 3.获取代理对象
    //     proxy = (IVoucherOrderService) AopContext.currentProxy();
    //
    //     // 4. 返回订单id
    //     return Result.ok(orderId);
    // }

    /**
     * 使用分布式锁来实现秒杀下单
     *
     * @param voucherId
     * @return
     */
    // @Override
    // public Result seckillVoucher(Long voucherId) {
    //     // 1.获取优惠券信息
    //     SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    //     // 2.判断秒杀是否开始
    //     LocalDateTime beginTime = voucher.getBeginTime();
    //     LocalDateTime endTime = voucher.getEndTime();
    //     if(beginTime.isAfter(LocalDateTime.now()) || endTime.isBefore(LocalDateTime.now())){
    //         return Result.fail("不再秒杀时段内！");
    //     }
    //     // 3.判断库存是否充足
    //     if(voucher.getStock() < 1){
    //         //库存不足
    //         return Result.fail("库存不足！");
    //     }
    //     Long userId = UserHolder.getUser().getId();
    //     // 这个代码我们不用了，下面要用Redisson中的分布式锁
    //     // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
    //     RLock lock = redissonClient.getLock("lock:order:" + userId);
    //     boolean isLock = lock.tryLock();
    //     // 判断是否获取锁成功
    //     if(!isLock){
    //         // 获取锁失败，返回错误和重试
    //        return Result.fail("不允许重复下单~");
    //     }
    //     try {
    //         // 获取代理对象（只有通过代理对象调用方法，事务才会生效）
    //         IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //         return proxy.createVoucherOrder(voucherId);
    //     } finally {
    //         lock.unlock();
    //     }
    // }
    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId) {
        // 4. 一人一单
        Long userId = UserHolder.getUser().getId();
        // 4.1 查询订单
        Integer count = this.query().eq("voucher_id", voucherId).eq("user_id", userId).count();
        // 4.2 判断是否存在
        if (count > 0) {
            return Result.fail("用户已经购买过一次了~");
        }


        // 5.扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").
                eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        //这里二次判断的原因在于：高并发场景下会有时间差A在更新库存的时间内，B把最后一件买走了，就会导致A更新失败！
        if (!success) {
            return Result.fail("库存不足！");
        }

        // 6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);

        // 6.2 用户id
        voucherOrder.setUserId(userId);

        // 6.3代金券id
        voucherOrder.setVoucherId(voucherId);
        this.save(voucherOrder);
        return Result.ok(orderId);
    }

    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {

        //注意：因为我们在Lua中已经校验过库存和一人一单了，这里就不需要校验拉~
        // 1.扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").
                eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        //这里其实不判断也是OK的，因为Lua脚本中校验过了，所以一定是充足的
        if (!success) {
            log.error("库存不足！");
        }

        // 2.保存订单
        this.save(voucherOrder);
    }
}
