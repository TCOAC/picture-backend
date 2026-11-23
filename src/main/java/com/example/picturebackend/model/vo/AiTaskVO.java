package com.example.picturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class AiTaskVO implements Serializable {
    private String taskId;
    private Long pictureId;
    private String status;
    private String message;
    private PictureVO result;
    private Date createdAt;
    private Date updatedAt;
    private static final long serialVersionUID = 1L;
}
