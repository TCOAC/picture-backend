package com.example.picturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.picturebackend.model.entity.Space;
import org.apache.ibatis.annotations.*;

public interface SpaceMapper extends BaseMapper<Space> {
    /** Reserve storage atomically; quotas cannot be exceeded by concurrent uploads. */
    @Update("UPDATE space SET totalCount=totalCount+#{count}, totalSize=totalSize+#{size} " +
            "WHERE id=#{id} AND isDelete=0 AND totalCount+#{count} BETWEEN 0 AND maxCount " +
            "AND totalSize+#{size} BETWEEN 0 AND maxSize")
    int adjustUsage(@Param("id") Long id, @Param("count") long count, @Param("size") long size);
}
