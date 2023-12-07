package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    IUserService iUserService;
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //判断到底是关注还是取关
        if (isFollow){
            //关注：新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);

            boolean save = save(follow);
            if (save){
                //把关注用户的id放入redis的set集合
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else {
            //取关：删除
            boolean remove = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            //把关注的用户id从redis移除
            if (remove){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }

        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        //1.查询是否关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        //判断是否关注
        return Result.ok(count > 0 );
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        //求关注交集
        String key = "follows:" + userId;
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        //解析出id
        if (intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }   
        List<Long> collect = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户
        List<UserDTO> userDTOS = iUserService
                .listByIds(collect)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
