package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    ISeckillVoucherService iSeckillVoucherService;

    @Resource
    RedisIdWorker redisIdWorker;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private  void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements  Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    //获取队列中的订单信息
                    VoucherOrder take = orderTasks.take();
                    //创建订单
                    handleVoucherOrder(take);
                } catch (Exception e) {
                    log.error("处理订单异常" ,e );
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder take) {
        //获取用户
        Long userId = take.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean b = lock.tryLock();
        //判断是否获取锁成功
        if (!b){
            //获取锁失败，返回错误请重试
            log.error("不允许重复下单");
            return ;
        }

        //如果要使用spring事物管理的话，需要获取到代理对象才能使用
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
             proxy.createVoucherOrder(take);
        } finally {
            //释放锁
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //2.判断结果是否为0
        int r = result.intValue();
        if (r != 0){
            //2.1 不为0，没有购买资格
            return Result.fail( r == 1 ? "库存不足" : "不能重复下单");
        }
        //2.2为0,有购买资格，把下单信息保存在阻塞队列中
        long orderId = redisIdWorker.nextId("order");

        //3.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();

        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        //代金券id
        voucherOrder.setVoucherId(voucherId);

        //创建阻塞队列
        orderTasks.add(voucherOrder);

        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //3. 返回订单id
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查找优惠券id
//        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //尚未开始
//            return Result.fail("秒杀未开始！");
//        }
//        //判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            //已经结束
//            return Result.fail("秒杀已结束！");
//        }
//
//        //3.判断库存是否充足
//
//        if (voucher.getStock() < 1){
//            //不充足，返回异常
//            return Result.fail("优惠券数量不足！");
//        }
//        //6.一人一单
//        Long userId = UserHolder.getUser().getId();
//        //创建锁对象
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean b = lock.tryLock();
//        //判断是否获取锁成功
//        if (!b){
//            //获取锁失败，返回错误请重试
//            return Result.fail("不允许重复下单");
//        }
//        //如果要使用spring事物管理的话，需要获取到代理对象才能使用
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId,userId);
//        } finally {
//            //释放锁
//            lock.unlock();
//        }
//
//    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();

        //6.1查询订单
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //6.2判断是否存在
        if (count > 0){
            log.error("用户已经购买过");
            return;
        }
        //4.扣减库存
        boolean success = iSeckillVoucherService.update().
                setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock",0)
                .update();
        if (!success) {
            log.error("获取优惠券失败");
            return ;
        }
//        //5.创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //用户id
//        voucherOrder.setUserId(UserHolder.getUser().getId());
//        //代金券id
//        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
//        //6.返回订单id
//        return Result.ok(orderId);
    }
}
