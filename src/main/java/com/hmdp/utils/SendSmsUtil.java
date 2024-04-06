package com.hmdp.utils;

/**
 * @author lxy
 * @version 1.0
 * @Description
 * @date 2022/11/21 22:24
 */
import com.hmdp.config.ResourceConfig;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.*;

/**
 * 腾讯云验证码发送工具类
 */
public class SendSmsUtil
{

    /**
     * 发送短信
     * @param phoneNumber 发送的手机号数组
     * @param templateParam 参数1：验证码；参数2：过期时间
     */
    public static void sendSms(String[] phoneNumber, String[] templateParam, ResourceConfig config){
        try{
            // 实例化一个认证对象，入参需要传入腾讯云账户secretId，secretKey,此处还需注意密钥对的保密
            // 密钥可前往https://console.cloud.tencent.com/cam/capi网站进行获取
            Credential cred = new Credential(config.getSmsSecretId(),config.getSmsSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("sms.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            SmsClient client = new SmsClient(cred, "ap-nanjing", clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            SendSmsRequest req = new SendSmsRequest();
            // req.se
            req.setSmsSdkAppId(config.getSmsSdkAppId());
            req.setSignName(config.getSmsSignName());
            req.setTemplateId(config.getSmsTemplateId());
            req.setPhoneNumberSet(phoneNumber);
            req.setTemplateParamSet(templateParam);

            // 返回的resp是一个SendSmsResponse的实例，与请求对象对应
            SendSmsResponse resp = client.SendSms(req);
            // 输出json格式的字符串回包
            System.out.println(SendSmsResponse.toJsonString(resp));
        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
        }
    }
    public static void main(String [] args) {

    }
}
