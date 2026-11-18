package com.example.picturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.IService;
import com.example.picturebackend.model.entity.Picture;
import com.example.picturebackend.model.tdo.picture.PictureQueryRequest;
import com.example.picturebackend.model.tdo.picture.PictureReviewRequest;
import com.example.picturebackend.model.vo.PictureVO;

import java.util.List;

/**
 * @author w
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2026-09-10 17:46:47
 */
public interface PictureService extends IService<Picture> {

    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest request);

    PictureVO getPictureVO(Picture picture);

    List<PictureVO> getPictureVOList(List<Picture> pictureList);

    void fillReviewParams(Picture picture, com.example.picturebackend.model.entity.User user);

    void doPictureReview(PictureReviewRequest request, com.example.picturebackend.model.entity.User user);

}
