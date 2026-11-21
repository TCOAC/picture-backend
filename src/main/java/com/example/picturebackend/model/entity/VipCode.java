package com.example.picturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("vip_code")
public class VipCode {
    @TableId(value = "codeHash", type = IdType.INPUT)
    private String codeHash;
    private Integer durationDays;
    private Long createdBy;
    private Date createTime;
    private Long redeemedBy;
    private Date redeemedAt;
}
