package com.hmdp.controller;


import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    IFollowService followService;
    /**
     * 关注&取关
     * @param id
     * @param isFollow
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable Long id, @PathVariable Boolean isFollow){
        return followService.follow(id,isFollow);
    }

    /**
     * 是否关注用户
     * @param id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable Long id){
        return followService.isFollow(id);
    }

    /**
     * 查看共同关注列表
     * @param id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable Long id){
        return followService.followCommons(id);
    }

}
