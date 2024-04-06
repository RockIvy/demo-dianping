package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.ResourceConfig;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SendSmsUtil;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl <UserMapper, User> implements IUserService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    ResourceConfig config;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }

        // 3.如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4.保存验证码到Redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code,RedisConstants.LOGIN_CODE_TTL,TimeUnit.MINUTES);

        // 5.发送验证码
        // String[] phoneNumber = new String[1];
        // String[] templateParam = new String[2];
        // phoneNumber[0] = phone;
        // templateParam[0] = code;
        // templateParam[1] = RedisConstants.LOGIN_CODE_TTL.toString();
        // SendSmsUtil.sendSms(phoneNumber,templateParam,config);
        log.info("发送验证码：" + code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.验证手机号是否正确
        String code = loginForm.getCode();
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2.查询手机号对应的验证码是否一致（存在）
        String cacheCode = (String) stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (cacheCode != null && !cacheCode.equals(code)) {
            // 3.不一致，报错
            return Result.fail("验证码错误！");
        }

        // 4.一致，根据手机号查询对应的用户
        User user = this.query().eq("phone", phone).one();
        // 删除验证码，防止用户多次使用
        stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_KEY + phone);
        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，则创建新用户，并保存到数据库
            user = createUserWithPhone(phone);
        }

        // 7.存在，则保存用户到Redis
        // 7.1 随机生成token,作为登录令牌   参数：生成一段没有连接符的随机数
        String token = UUID.randomUUID().toString(true);
        // 7.2 准备用户基本信息
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //注意：这里如果直接这样写 BeanUtil.beanToMap(userDTO) 会报错: Long cannot be cast to String。
        //     因为我们使用的是stringRedisTemplate,里面存的map的key和value都必须是String.
        //解决办法：1.不使用该方法，而是手动把userDTO放入Map<String,String>，该转换的手动转
        //        2.自定义value的转换规则，可以通过 BeanUtil.beanToMap(obj,map,copyOptions)
        Map <String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap <>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3 保存到Redis  （这里也可使用put（），但是Value中的key和value是分开放的，需要多次和数据库交互~）
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, map);
        // 7.4 设置token有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.把token返回给前端
        return Result.ok(token);
    }

    /**
     * 创建新用户
     *
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setIcon("https://blog-photos-lxy.oss-cn-hangzhou.aliyuncs.com/img/202310051904835.png");
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        this.save(user);
        return user;
    }
}
