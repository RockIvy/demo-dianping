package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author lxy
 * @version 1.0
 * @Description
 * @date 2022/12/5 21:10
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
