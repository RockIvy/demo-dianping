package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author lxy
 * @version 1.0
 * @Description MVC配置类
 *
 * 登录的验证流程总结：
 * ① 每次请求先进入RefreshTokenInterceptor拦截器
 * ② 判断token是否为空,为空则未登录,进入下一个LoginInterceptor拦截器【同理，token不为空，但是UserMap为空，说明用户登录信息已过期。也进入登录拦截器】
 * ③ token不为空则从Redis取用户信息保存到ThreadLocal的UserHolder中
 * ④ 然后进入LoginInterceptor,此时可以通过UserHolder.getUser()获取到用户信息
 * ⑤ 如果用户不为空,已登录,放行到Controller
 * ⑥ 用户为空,拦截返回401未授权
 * ⑦ 最后在RefreshTokenInterceptor的afterCompletion中清理ThreadLocal
 * ⑧ 这样每个请求都验证token,每个线程只维护本次请求用户信息
 * 请求结束threadlocal被清理,防止内存泄漏
 *
 * 注意：
 *
 * @date 2022/11/24 1:22
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 增加登录拦截器，并对不必要的请求路径排除拦截
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                "/user/login",
                "/user/code",
                "/blog/hot",
                "/shop/**",
                "/shop-type/**",
                "/upload/**",
                "/voucher/**"
        ).order(1);
        // 刷新凭证拦截器  order:拦截器执行的顺序，如果不设定则按照添加的顺序
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
    }
}
