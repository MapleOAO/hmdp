package com.hmdp.service.impl;

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
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.locks.Lock;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    ISeckillVoucherService iSeckillVoucherService;

    @Resource
    RedisIdWorker redisIdWorker;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查找优惠券id
        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("秒杀未开始！");
        }
        //判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //已经结束
            return Result.fail("秒杀已结束！");
        }

        //3.判断库存是否充足

        if (voucher.getStock() < 1){
            //不充足，返回异常
            return Result.fail("优惠券数量不足！");
        }
        //6.一人一单
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        boolean b = simpleRedisLock.tryLock(5);
        //判断是否获取锁成功
        if (!b){
            //获取锁失败，返回错误请重试
            return Result.fail("不允许重复下单");
        }
        //如果要使用spring事物管理的话，需要获取到代理对象才能使用
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId,userId);
        } finally {
            //释放锁
            simpleRedisLock.unlock();
        }

    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId) {
        //6.1查询订单
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //6.2判断是否存在
        if (count > 0){
            return Result.fail("用户已经购买过！");
        }
        //4.扣减库存
        boolean success = iSeckillVoucherService.update().
                setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if (!success) {
            return Result.fail("获取优惠券失败！");
        }
        //5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //6.返回订单id
        return Result.ok(orderId);
    }
}
