package com.hmdp.config;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * @author lxy
 * @version 1.0
 * @Description
 * @date 2022/11/30 18:20
 */
@Configuration
@ConfigurationProperties(prefix = "com.hmdp.resource")
@Data
public class ResourceConfig {

    /**
     * 短信服务秘钥ID
     */
    private  String smsSecretId;

    /**
     * 短信服务KEY
     */
    private String smsSecretKey;

    /**
     * AppID
     */
    private  String smsSdkAppId;

    /**
     * 签名
     */
    private  String smsSignName;

    /**
     * 模板ID
     */
    private  String smsTemplateId;
}
