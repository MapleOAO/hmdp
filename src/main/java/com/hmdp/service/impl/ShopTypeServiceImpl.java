package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    ShopTypeMapper shopTypeMapper;
    @Override
    public List<ShopType> queryList() {
        //1.从redis获取ShopTypeList集合
        String shopType = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        if (!(shopType == null)) {
            //2.获取到集合，返回给前端
            return JSONUtil.toList(shopType, ShopType.class);
        }
        //3.从数据库查询
        List<ShopType> shopTypes = shopTypeMapper.selectList(null);
        //4.缓存到redis
        String jsonStr = JSONUtil.toJsonStr(shopTypes);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY,jsonStr);
        //5.返回给前端
        return shopTypes;
    }
}
