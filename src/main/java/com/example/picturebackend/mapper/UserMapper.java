package com.example.picturebackend.mapper;

import com.example.picturebackend.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
* @author w
* @description 针对表【user(用户)】的数据库操作Mapper
* @createDate 2026-08-30 15:32:12
* @Entity com.example.picturebackend.model.entity.User
*/
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM user WHERE id = #{id} AND isDelete = 0 FOR UPDATE")
    User lockById(@Param("id") Long id);
}




