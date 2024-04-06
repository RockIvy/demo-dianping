package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page <Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List <Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 查询博客是否被当前用户点赞
     * @param blog
     */
    public void isBlogLiked(Blog blog) {
        String key = RedisConstants.BLOG_LIKED_KEY+blog.getId();
        UserDTO user = UserHolder.getUser();
        if(user!=null){
            Long userId = user.getId();
            Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
            blog.setIsLike(score!=null);
        }
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = this.getById(id);
        if(blog==null){
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 点赞
     * TODO 思考：这里需不需要考虑事务&并发的问题？
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY+id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score!=null) {
            // 已经点过赞了，则取消点赞
            boolean isSuccess = this.update().setSql("liked = liked - 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }else{
            // 未点过赞，则进行点赞
            boolean isSuccess = this.update().setSql("liked = liked + 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set <String> set = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(set==null || set.size() == 0){
            return Result.ok(new ArrayList <>());
        }
        List <Long> ids = set.stream().map(Long::valueOf).collect(Collectors.toList());
        // 注意：in语法 会导致查询的结果并不按照传入的id的顺序，而是按照默认的从小到大的顺序~ 可以指定字段的顺序
        // ... WHERE (id IN (2,4,5,1)) ORDER BY FIELD(id,2,4,5,1)
        List <User> userList = userService.list(new LambdaQueryWrapper <User>().in(User::getId, ids).last("ORDER BY FIELD(id,"+ StrUtil.join(",",ids) +")"));
        List <UserDTO> userDTOs = userList.stream().map(user -> {
            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(user, userDTO);
            return userDTO;
        }).collect(Collectors.toList());
        return Result.ok(userDTOs);
    }

    public void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
