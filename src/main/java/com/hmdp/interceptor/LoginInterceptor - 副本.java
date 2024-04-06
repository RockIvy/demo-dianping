package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author lxy
 * @version 1.0
 * @Description 登录拦截器
 * @date 2022/11/24 1:12
 */
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 在进入Controller之前会被执行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 如果没有登录则拦截
        if(UserHolder.getUser()==null){
            response.setStatus(401);
            return false;
        }
        // 用户已经登录则放行
        return true;
    }

}
