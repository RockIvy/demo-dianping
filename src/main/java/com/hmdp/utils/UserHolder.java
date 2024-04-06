package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;

/**
 * @author lxy
 * @version 1.0
 * @Description 保存User到ThreadLocal的工具类
 * @date 2022/11/24 1:22
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
