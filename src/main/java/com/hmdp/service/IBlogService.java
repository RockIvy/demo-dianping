package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    /**
     * 点赞
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 查询博客点赞
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);
}
