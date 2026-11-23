package com.example.picturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class ExternalImageSearchResult implements Serializable {
    private String thumbUrl;
    private String fromUrl;
    private String title;
    private static final long serialVersionUID = 1L;
}
