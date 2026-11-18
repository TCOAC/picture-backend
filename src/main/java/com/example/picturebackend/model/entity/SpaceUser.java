package com.example.picturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("space_user")
public class SpaceUser {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long spaceId;
    private Long userId;
    /** viewer, editor, admin. */
    private String spaceRole;
    private Date createTime;
}
