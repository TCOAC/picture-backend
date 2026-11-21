package com.example.picturebackend.model.tdo.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureSimilarSearchRequest implements Serializable {
    private Long pictureId;
    private Long spaceId;
    private static final long serialVersionUID = 1L;
}
