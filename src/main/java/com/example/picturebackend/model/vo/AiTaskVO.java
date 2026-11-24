package com.example.picturebackend.model.vo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.Date;

@Data
public class AiTaskVO implements Serializable {
    private String taskId;
    private Long pictureId;
    @JsonIgnore
    private Long requesterId;
    private volatile String status;
    private volatile String message;
    private volatile PictureVO result;
    private Date createdAt;
    private volatile Date updatedAt;
    private static final long serialVersionUID = 1L;
}
