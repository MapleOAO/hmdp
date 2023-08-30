package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    ShopMapper shopMapper;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
//
//
//        //1.从redis中查询商铺缓存
//        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(CACHE_SHOP_KEY + id);
//
//        if (!shopMap.isEmpty()) {
//            //如果redis中的值为null也返回不存在
//            if (shopMap.get("null") == null) {
//                return Result.fail("商铺不存在");
//            }
//            //2.redis中存在，返回结果到前端
//            Shop shop = BeanUtil.toBean(shopMap, Shop.class);
//            return Result.ok(shop);
//        }
//
//        //3.数据库查询
//        Shop shop = getById(id);
//
//        if (shop == null) {
//            HashMap<String,String> nullMap = new HashMap<String,String>();
//            nullMap.put("null",null);
//            //rides写入空值
//            stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id,nullMap);
//            stringRedisTemplate.opsForHash().getOperations().expire(CACHE_SHOP_KEY + id,CACHE_NULL_TTL, TimeUnit.MINUTES);
//            //4.数据库查询不存在，报错“商铺不存在”
//            return Result.fail("商铺不存在");
//        }
//        //4.数据库查询结果缓存redis
//        //将所有内容转换为String类型，因为stringRedisTemplate只能存储string类型
//        Map<String, Object> beanToMap = BeanUtil.beanToMap(shop, new HashMap<>(), CopyOptions.create()
//                .setIgnoreNullValue(true)
//                .setFieldValueEditor((fieldName, fieldValue) ->
//                {
//                    if (fieldValue == null){
//
//                    }else {
//                        fieldValue = fieldValue.toString();
//                    }
//                    return fieldValue;
//                }));
//
//        stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id,beanToMap);
//        stringRedisTemplate.opsForHash().getOperations().expire(CACHE_SHOP_KEY + id,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //5.返回查询结果到前端
//        return Result.ok(shop);
       //解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null){
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    @Override
    //需要加事物
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺ID不能改为空");
        }
        //先更新数据库
        shopMapper.updateById(shop);
        //再删除缓存
        stringRedisTemplate.opsForHash().delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }
}
