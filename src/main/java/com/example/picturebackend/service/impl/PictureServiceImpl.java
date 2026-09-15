package com.example.picturebackend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.picturebackend.mapper.PictureMapper;
import com.example.picturebackend.model.entity.Picture;
import com.example.picturebackend.service.PictureService;
import org.springframework.stereotype.Service;

/**
 * @author w
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-09-10 17:46:47
 */
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

}
