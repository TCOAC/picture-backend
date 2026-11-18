package com.example.picturebackend.model.tdo.picture;

import com.example.picturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class PictureQueryRequest extends PageRequest implements Serializable {

    private String searchText;

    private String category;
    private Integer minWidth;
    private Integer minHeight;
    private Long maxSize;
    private String picFormat;

    private String tag;

    private Long userId;

    private Long spaceId;

    private Integer reviewStatus;

    private String reviewMessage;

    private Long reviewerId;

    /** Server-controlled on admin listings only. */
    private Boolean includeAllSpaces;

    private static final long serialVersionUID = 1L;
}
