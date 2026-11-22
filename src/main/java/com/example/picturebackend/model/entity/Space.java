package com.example.picturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("space")
public class Space {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String spaceName;
    private Integer spaceLevel;
    /** 0 private, 1 team. */
    private Integer spaceType;
    private Long userId;
    private Long maxSize;
    private Long maxCount;
    private Long totalSize;
    private Long totalCount;
    private Date createTime;
    private Date updateTime;
    @TableLogic
    private Integer isDelete;
    @TableField(exist = false)
    private String role;
}
