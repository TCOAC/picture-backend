package com.example.picturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.picturebackend.model.entity.VipCode;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface VipCodeMapper extends BaseMapper<VipCode> {
    @Update("UPDATE vip_code SET redeemedBy = #{userId}, redeemedAt = CURRENT_TIMESTAMP " +
            "WHERE codeHash = #{codeHash} AND redeemedAt IS NULL")
    int claim(@Param("codeHash") String codeHash, @Param("userId") Long userId);
}
