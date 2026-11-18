package com.example.picturebackend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.picturebackend.exception.BusinessException;
import com.example.picturebackend.exception.ErrorCode;
import com.example.picturebackend.mapper.PictureMapper;
import com.example.picturebackend.model.entity.Picture;
import com.example.picturebackend.model.entity.User;
import com.example.picturebackend.model.tdo.picture.PictureQueryRequest;
import com.example.picturebackend.model.tdo.picture.PictureReviewRequest;
import com.example.picturebackend.model.enums.PictureReviewStatusEnum;
import com.example.picturebackend.model.vo.PictureVO;
import com.example.picturebackend.model.vo.UserVO;
import com.example.picturebackend.service.PictureService;
import com.example.picturebackend.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author w
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-09-10 17:46:47
 */
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    private static final List<String> ALLOWED_SORT_FIELDS = Arrays.asList(
            "createTime", "updateTime", "editTime", "name", "picSize");

    @Resource
    private UserService userService;

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "查询参数不能为空");
        }
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        String searchText = StrUtil.trim(request.getSearchText());
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(wrapper -> wrapper.like("name", searchText)
                    .or().like("introduction", searchText));
        }
        queryWrapper.eq(StrUtil.isNotBlank(request.getCategory()), "category", request.getCategory());
        queryWrapper.ge(request.getMinWidth()!=null,"picWidth",request.getMinWidth());
        queryWrapper.ge(request.getMinHeight()!=null,"picHeight",request.getMinHeight());
        queryWrapper.le(request.getMaxSize()!=null,"picSize",request.getMaxSize());
        queryWrapper.eq(StrUtil.isNotBlank(request.getPicFormat()),"picFormat",request.getPicFormat());
        queryWrapper.like(StrUtil.isNotBlank(request.getTag()), "tags", request.getTag());
        queryWrapper.eq(request.getUserId() != null, "userId", request.getUserId());
        if (!Boolean.TRUE.equals(request.getIncludeAllSpaces())) {
            if (request.getSpaceId() == null) queryWrapper.isNull("spaceId");
            else queryWrapper.eq("spaceId", request.getSpaceId());
        } else {
            queryWrapper.eq(request.getSpaceId() != null, "spaceId", request.getSpaceId());
        }
        queryWrapper.eq(request.getReviewStatus() != null, "reviewStatus", request.getReviewStatus());
        queryWrapper.like(StrUtil.isNotBlank(request.getReviewMessage()), "reviewMessage", request.getReviewMessage());
        queryWrapper.eq(request.getReviewerId()!=null, "reviewerId", request.getReviewerId());
        String sortField = request.getSortField();
        if (ALLOWED_SORT_FIELDS.contains(sortField)) {
            queryWrapper.orderBy(true, "ascend".equals(request.getSortOrder()), sortField);
        } else {
            queryWrapper.orderByDesc("createTime");
        }
        return queryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture) {
        if (picture == null) {
            return null;
        }
        PictureVO pictureVO = PictureVO.objToVo(picture);
        if (picture.getUserId() != null) {
            User user = userService.getById(picture.getUserId());
            pictureVO.setUser(userService.getUserVO(user));
            if(pictureVO.getUser()!=null)pictureVO.getUser().setUserAccount(null);
        }
        return pictureVO;
    }

    @Override
    public List<PictureVO> getPictureVOList(List<Picture> pictureList) {
        if (pictureList == null || pictureList.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = pictureList.stream().map(Picture::getUserId).filter(id -> id != null).distinct().collect(Collectors.toList());
        Map<Long, UserVO> users = userService.listByIds(userIds).stream()
                .map(userService::getUserVO).collect(Collectors.toMap(UserVO::getId, Function.identity(), (left, right) -> left));
        return pictureList.stream().map(picture -> {
            PictureVO vo = PictureVO.objToVo(picture);
            vo.setUser(users.get(picture.getUserId()));
            if(vo.getUser()!=null)vo.getUser().setUserAccount(null);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void fillReviewParams(Picture picture, User user) {
        if (userService.isAdmin(user) || picture.getSpaceId()!=null) {
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(user.getId());
            picture.setReviewMessage(picture.getSpaceId()!=null?"空间内部图片":"管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
            picture.setReviewerId(null);
            picture.setReviewMessage(null);
            picture.setReviewTime(null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void doPictureReview(PictureReviewRequest request, User user) {
        if (!userService.isAdmin(user)) throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        PictureReviewStatusEnum status = request == null ? null : PictureReviewStatusEnum.getEnumByValue(request.getReviewStatus());
        if (request == null || request.getId() == null || request.getId() <= 0 || status == null
                || status == PictureReviewStatusEnum.REVIEWING) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核参数不合法");
        }
        if (request.getReviewMessage() != null && request.getReviewMessage().length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核说明不能超过 512 字");
        }
        Picture oldPicture = getById(request.getId());
        if (oldPicture == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        if (status.getValue() == (oldPicture.getReviewStatus() == null ? 0 : oldPicture.getReviewStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        oldPicture.setReviewStatus(status.getValue());
        oldPicture.setReviewMessage(request.getReviewMessage() == null ? "" : request.getReviewMessage().trim());
        oldPicture.setReviewerId(user.getId());
        oldPicture.setReviewTime(new Date());
        if (!updateById(oldPicture)) throw new BusinessException(ErrorCode.OPERATION_ERROR, "审核状态保存失败");
    }

}
