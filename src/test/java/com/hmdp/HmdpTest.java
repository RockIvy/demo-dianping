package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Voucher;
import org.junit.Test;

import java.time.LocalDateTime;

/**
 * @author lxy
 * @version 1.0
 * @Description
 * @date 2023/2/6 15:41
 */
public class HmdpTest {
    @Test
    public void test(){
        Voucher voucher = new Voucher();
        voucher.setShopId(1L);
        voucher.setTitle("500元代金券");
        voucher.setTitle("周一至周五均可使用");
        voucher.setRules("全场通用\\n无需预约\\n可无限叠加\\不兑现、不找零\\n仅限堂食");
        voucher.setPayValue(8000L);
        voucher.setActualValue(10000L);
        voucher.setType(1);
        voucher.setStock(100);
        voucher.setBeginTime(LocalDateTime.of(2023,1,1,00,00,00));
        voucher.setEndTime(LocalDateTime.of(2023,12,12,23,59,59));
        System.out.println(JSONUtil.toJsonStr(voucher));
    }
}
