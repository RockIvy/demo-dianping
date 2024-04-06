package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long id, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOWS_USER_KEY+userId;
        if(isFollow){
            // 关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean isSuccess = this.save(follow);
            if(isSuccess){
                stringRedisTemplate.opsForSet().add(key,id.toString());
            }
        }else {
            // 取消关注
            boolean isSuccess = this.remove(new LambdaQueryWrapper <Follow>().eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, id));
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key,id.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        int count = this.count(new LambdaQueryWrapper <Follow>().eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, id));
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key1 = RedisConstants.FOLLOWS_USER_KEY + id.toString();
        String key2 = RedisConstants.FOLLOWS_USER_KEY + userId.toString();
        Set <String> idSet = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (CollectionUtil.isEmpty(idSet)) {
            return Result.ok(Collections.EMPTY_LIST);
        }
        List <Long> idList = idSet.stream().map(Long::valueOf).collect(Collectors.toList());
        List <UserDTO> userDTOList = userService.listByIds(idList).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
