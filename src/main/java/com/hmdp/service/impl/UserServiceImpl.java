package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            //2.不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码到redis  // set key value ex 120
        //session.setAttribute("code",code);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone , code, LOGIN_CODE_TTL , TimeUnit.MINUTES);
        //5.发送验证码
        log.debug("发送短信验证码成功！验证码：{}",code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号和验证码
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            //2.手机号格式不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        if (RegexUtils.isCodeInvalid(loginForm.getCode())) {
            //2.验证码格式不符合，返回错误信息
            return Result.fail("验证码格式错误！");
        }
        //3.从redis获取验证码并且校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        if(cacheCode == null ||!cacheCode.equals(loginForm.getCode())){
            return Result.fail("验证码错误!");
        }
        if (session.getAttribute("code") == null || !session.getAttribute("code").toString().equals(loginForm.getCode())) {
            //3.验证码是否相同
            return Result.fail("验证码错误!");
        }
        //4.判断用户是否存在
        User user = query().eq("phone", loginForm.getPhone()).one();
        if (user == null){
            //5.新用户需要注册
            user = createUserWithPhone(loginForm.getPhone());
        }

        //6.保存登录信息到redis
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        //7.1随机生成token
        String token = UUID.randomUUID().toString(true);

        //7.2user转为hashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //将所有内容转换为String类型，因为stringRedisTemplate只能存储string类型
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //7.3存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //7.4设置有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //8返回token
        return Result.ok(token);
    }

    @Override
    public Result getUser(Long userId) {
        User user = getById(userId);
        if (user == null){
            return Result.fail("无用户！");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        return Result.ok(userDTO);
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //保存用户
        save(user);
        return user;
    }
}
