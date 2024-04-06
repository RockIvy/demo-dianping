package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author lxy
 * @version 1.0
 * @Description Token刷新拦截器
 * @date 2022/11/24 1:12
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    /**
     * 因为RefreshTokenInterceptor是new 出来的，并没有交给Spring容器管理，所以我们不能使用Autowire或者@Resource注入，
     * 这里可以使用构造函数，在使用到拦截器的时候，传入需要的对象
     */
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 在进入Controller之前会被执行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 此处return true是对的，若return false，第一次访问登录页面时就会被拦截；
            // 若return true，第一次访问登录页会进入Login拦截器，由于登录页为放行路径，放行~
            return true;
        }

        // 2.根据token获取用户信息
        Map <Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY+token);
        if(userMap==null || userMap.size() == 0){
            return true;
        }

        // 3.如果存在，则保存到ThreadLocal
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);

        // 4.刷新用户token的有效时间 (只要用户在这段时间内用户在线，那么就不会过期)
        String tokenKey = RedisConstants.LOGIN_USER_KEY+token;
        stringRedisTemplate.expire(tokenKey,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 5.放行
        return true;
    }

    /**
     * 使用 ThreadLocal 时候,最好在请求结束后做 remove 操作,避免出现内存泄漏。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
