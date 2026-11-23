package com.example.picturebackend.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.picturebackend.common.BaseResponse;
import com.example.picturebackend.common.DeleteRequest;
import com.example.picturebackend.common.ResultUtils;
import com.example.picturebackend.constant.UserConstant;
import com.example.picturebackend.domain.space.SpaceDomainService;
import com.example.picturebackend.exception.BusinessException;
import com.example.picturebackend.exception.ErrorCode;
import com.example.picturebackend.exception.ThrowUtils;
import com.example.picturebackend.manager.FileManager;
import com.example.picturebackend.model.entity.Picture;
import com.example.picturebackend.model.entity.User;
import com.example.picturebackend.model.enums.PictureReviewStatusEnum;
import com.example.picturebackend.model.tdo.file.UploadPictureResult;
import com.example.picturebackend.model.tdo.picture.PictureQueryRequest;
import com.example.picturebackend.model.tdo.picture.PictureReviewRequest;
import com.example.picturebackend.model.tdo.picture.PictureUploadRequest;
import com.example.picturebackend.model.vo.PictureVO;
import com.example.picturebackend.model.vo.ExternalImageSearchResult;
import com.example.picturebackend.service.PictureService;
import com.example.picturebackend.service.PublicGalleryCache;
import com.example.picturebackend.service.UserService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/picture")
public class PictureController {
    private static final java.security.SecureRandom SHARE_RANDOM=new java.security.SecureRandom();

    private static final int MAX_PAGE_SIZE = 50;

    @Resource private PictureService pictureService;
    @Resource private UserService userService;
    @Resource private FileManager fileManager;
    @Resource private SpaceDomainService spaceService;
    @Resource private com.example.picturebackend.domain.space.PictureAccess access;
    @Resource private com.example.picturebackend.domain.space.CollaborationLocks locks;
    @Resource private PublicGalleryCache publicGalleryCache;
    @Resource private com.example.picturebackend.service.ExternalImageSearchService externalImageSearch;

    /** Upload/create or replace an image. Members' changes return to the moderation queue. */
    @PostMapping("/upload")
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String introduction,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tags,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Picture oldPicture = null;
        if (id != null) {
            oldPicture = pictureService.getOne(new QueryWrapper<Picture>().eq("id", id).last("FOR UPDATE"));
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            checkPictureEditPermission(oldPicture, loginUser);
            ThrowUtils.throwIf(spaceId != null && !spaceId.equals(oldPicture.getSpaceId()),
                    ErrorCode.PARAMS_ERROR, "不能在替换图片时更改所属空间");
            spaceId = oldPicture.getSpaceId();
        }
        spaceService.check(spaceId, loginUser, "edit");
        long oldSize = oldPicture == null || oldPicture.getPicSize() == null ? 0 : oldPicture.getPicSize();
        spaceService.adjustUsage(spaceId, oldPicture == null ? 1 : 0, file.getSize() - oldSize);
            UploadPictureResult uploadResult = fileManager.uploadPicture(file, "picture/" + loginUser.getId());
            long sizeDelta = uploadResult.getPicSize() - file.getSize();
            if (sizeDelta != 0) spaceService.adjustUsage(spaceId, 0, sizeDelta);

            Picture picture = oldPicture == null ? new Picture() : oldPicture;
            picture.setUrl(uploadResult.getUrl());
            picture.setName(StringUtils.hasText(name) ? name.trim() : uploadResult.getPicName());
            picture.setIntroduction(trimToNull(introduction));
            picture.setCategory(trimToNull(category));
            picture.setTags(normalizeTags(tags));
            picture.setPicSize(uploadResult.getPicSize());
            picture.setPicWidth(uploadResult.getPicWidth());
            picture.setPicHeight(uploadResult.getPicHeight());
            picture.setPicScale(uploadResult.getPicScale());
            picture.setPicFormat(uploadResult.getPicFormat());
            picture.setFileHash(uploadResult.getFileHash());
            picture.setPicColor(uploadResult.getPicColor());
            picture.setPerceptualHash(uploadResult.getPerceptualHash());
            picture.setSpaceId(spaceId);
            if (oldPicture == null) {
                picture.setUserId(loginUser.getId());
                picture.setViewCount(0L);
                picture.setDownloadCount(0L);
            } else {
                picture.setEditTime(new Date());
            }
            validateMetadata(picture);
            pictureService.fillReviewParams(picture, loginUser);
            boolean saved = oldPicture == null ? pictureService.save(picture) : pictureService.updateById(picture);
            ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "图片信息保存失败");
            if (picture.getSpaceId() == null) publicGalleryCache.invalidateAfterCommit();
            return ResultUtils.success(pictureService.getPictureVO(picture));
    }

    /** Public gallery: only approved images outside private/team spaces are visible. */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR, "查询参数不能为空");
        queryRequest.setSpaceId(null);
        queryRequest.setIncludeAllSpaces(false);
        queryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        return ResultUtils.success(publicGalleryCache.get(queryRequest, () -> pageData(queryRequest)));
    }


    /** Current member's uploads, including pending or rejected items. */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<PictureVO>> listMyPictureVOByPage(@RequestBody PictureQueryRequest queryRequest,
                                                               HttpServletRequest request) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User user = userService.getLoginUser(request);
        spaceService.check(queryRequest.getSpaceId(), user, "read");
        queryRequest.setUserId(user.getId());
        queryRequest.setIncludeAllSpaces(false);
        return listPage(queryRequest);
    }

    /** Moderation view, including private/team pictures and every review state. */
    @PostMapping("/admin/list/page/vo")
    @com.example.picturebackend.annotation.AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<PictureVO>> listAllPictureVOByPage(@RequestBody PictureQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        queryRequest.setIncludeAllSpaces(true);
        return listPage(queryRequest);
    }

    private BaseResponse<Page<PictureVO>> listPage(PictureQueryRequest queryRequest) {
        return ResultUtils.success(pageData(queryRequest));
    }

    private Page<PictureVO> pageData(PictureQueryRequest queryRequest) {
        int current = Math.max(1, queryRequest.getCurrent());
        int pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, queryRequest.getPageSize()));
        queryRequest.setCurrent(current);
        queryRequest.setPageSize(pageSize);
        QueryWrapper<Picture> queryWrapper = pictureService.getQueryWrapper(queryRequest);
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), queryWrapper);
        Page<PictureVO> resultPage = new Page<>(current, pageSize, picturePage.getTotal());
        resultPage.setRecords(pictureService.getPictureVOList(picturePage.getRecords()));
        return resultPage;
    }

    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(@RequestParam("id") long id, HttpServletRequest request,
                                                    @RequestParam(required=false) String code) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        access.read(picture, request, code);
        pictureService.update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Picture>()
                .eq("id", id).setSql("viewCount = viewCount + 1"));
        return ResultUtils.success(pictureService.getPictureVO(picture));
    }

    @GetMapping("/tag_category")
    public BaseResponse<java.util.Map<String, java.util.List<String>>> listTagCategory() {
        java.util.List<Picture> pictures = pictureService.list(new QueryWrapper<Picture>()
                .select("category", "tags")
                .isNull("spaceId")
                .eq("reviewStatus", PictureReviewStatusEnum.PASS.getValue())
                .and(query -> query.isNotNull("category").or().isNotNull("tags"))
                .last("LIMIT 5000"));
        java.util.Set<String> categories = new java.util.TreeSet<>();
        java.util.Set<String> tags = new java.util.TreeSet<>();
        for (Picture picture : pictures) {
            if (StringUtils.hasText(picture.getCategory())) categories.add(picture.getCategory().trim());
            if (StringUtils.hasText(picture.getTags())) {
                try { tags.addAll(JSONUtil.toList(picture.getTags(), String.class)); }
                catch (Exception ignored) { }
            }
        }
        java.util.Map<String, java.util.List<String>> result = new java.util.LinkedHashMap<>();
        result.put("categoryList", new java.util.ArrayList<>(categories));
        result.put("tagList", new java.util.ArrayList<>(tags));
        return ResultUtils.success(result);
    }

    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureUploadRequest editRequest,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(editRequest == null || editRequest.getId() == null || editRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Picture picture = pictureService.getById(editRequest.getId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        checkPictureEditPermission(picture, loginUser);
        if (editRequest.getName() != null) picture.setName(trimToNull(editRequest.getName()));
        if (editRequest.getIntroduction() != null) picture.setIntroduction(trimToNull(editRequest.getIntroduction()));
        if (editRequest.getCategory() != null) picture.setCategory(trimToNull(editRequest.getCategory()));
        if (editRequest.getTags() != null) picture.setTags(normalizeTags(editRequest.getTags()));
        picture.setEditTime(new Date());
        validateMetadata(picture);
        pictureService.fillReviewParams(picture, loginUser);
        boolean updated = pictureService.updateById(picture);
        if (updated && picture.getSpaceId() == null) publicGalleryCache.invalidateAfterCommit();
        return ResultUtils.success(updated);
    }

    @PostMapping("/review")
    @com.example.picturebackend.annotation.AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> reviewPicture(@RequestBody PictureReviewRequest reviewRequest,
                                               HttpServletRequest request) {
        pictureService.doPictureReview(reviewRequest, userService.getLoginUser(request));
        publicGalleryCache.invalidateAfterCommit();
        return ResultUtils.success(true);
    }

    @PostMapping("/delete")
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User user = userService.getLoginUser(request);
        Picture picture = pictureService.getOne(new QueryWrapper<Picture>().eq("id",deleteRequest.getId()).last("FOR UPDATE"));
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        checkPictureEditPermission(picture, user);
        spaceService.adjustUsage(picture.getSpaceId(), -1, -(picture.getPicSize() == null ? 0 : picture.getPicSize()));
        ThrowUtils.throwIf(!pictureService.removeById(picture.getId()), ErrorCode.OPERATION_ERROR);
        if (picture.getSpaceId() == null) publicGalleryCache.invalidateAfterCommit();
        return ResultUtils.success(true);
    }

    @PostMapping("/batch/edit")
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse<Integer> batchEdit(@RequestBody PictureBatchEditRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getIds() == null || body.getIds().isEmpty()
                || body.getIds().size() > 100, ErrorCode.PARAMS_ERROR, "一次最多编辑 100 张图片");
        User user = userService.getLoginUser(request);
        int count = 0;
        boolean publicChanged = false;
        for (Long id : body.getIds()) {
            Picture picture = pictureService.getById(id);
            ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
            checkPictureEditPermission(picture, user);
            if (body.getCategory() != null) picture.setCategory(trimToNull(body.getCategory()));
            if (body.getTags() != null) picture.setTags(normalizeTags(body.getTags()));
            if (StringUtils.hasText(body.getNameRule())) {
                picture.setName(body.getNameRule().trim().replace("{序号}", String.valueOf(count + 1)));
            }
            validateMetadata(picture);
            pictureService.fillReviewParams(picture, user);
            ThrowUtils.throwIf(!pictureService.updateById(picture), ErrorCode.OPERATION_ERROR);
            publicChanged |= picture.getSpaceId() == null;
            count++;
        }
        if (publicChanged) publicGalleryCache.invalidateAfterCommit();
        return ResultUtils.success(count);
    }


    @PostMapping("/share")
    public BaseResponse<String> createShare(@RequestBody DeleteRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getId() == null, ErrorCode.PARAMS_ERROR);
        User user = userService.getLoginUser(request);
        Picture picture = pictureService.getById(body.getId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        checkPictureEditPermission(picture, user);
        ThrowUtils.throwIf(picture.getSpaceId()==null && !Integer.valueOf(1).equals(picture.getReviewStatus()),
                ErrorCode.OPERATION_ERROR, "公共图片审核通过后才可分享");
        picture.setShareCode(newShareToken());
        picture.setShareExpireTime(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000));
        pictureService.update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Picture>()
                .eq("id",picture.getId()).set("shareCode",picture.getShareCode()).set("shareExpireTime",picture.getShareExpireTime()));
        return ResultUtils.success(picture.getShareCode());
    }

    @GetMapping("/share/get")
    public BaseResponse<PictureVO> getSharedPicture(@RequestParam String code) {
        Picture picture = pictureService.getOne(new QueryWrapper<Picture>().eq("shareCode", code));
        ThrowUtils.throwIf(picture == null || picture.getShareExpireTime() == null
                || !access.shared(picture,code), ErrorCode.NOT_FOUND_ERROR, "分享链接已失效");
        pictureService.update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Picture>().eq("id",picture.getId()).setSql("viewCount=viewCount+1"));
        return ResultUtils.success(pictureService.getPictureVO(picture));
    }

    private void ensureReadable(Picture picture, HttpServletRequest request) {
        access.read(picture,request,null);
    }
    public void assertCanRead(Picture picture,HttpServletRequest request,String shareCode){access.read(picture,request,shareCode);}
    public PictureVO toPictureVO(Picture picture){return pictureService.getPictureVO(picture);}
    public void incrementDownload(Long id){pictureService.update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Picture>().eq("id",id).setSql("downloadCount=downloadCount+1"));}
    public Picture findByStorageKey(String key){return pictureService.getOne(new QueryWrapper<Picture>().eq("url","/api/media/"+key).last("LIMIT 1"));}

    @PostMapping("/search/external")
    public BaseResponse<List<ExternalImageSearchResult>> searchExternal(
            @RequestBody com.example.picturebackend.model.tdo.picture.PictureSimilarSearchRequest body,
            HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getPictureId() == null,
                ErrorCode.PARAMS_ERROR, "图片不能为空");
        Picture picture = pictureService.getById(body.getPictureId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        access.read(picture, request, null);
        return ResultUtils.success(externalImageSearch.search(picture.getUrl()));
    }
    @PostMapping("/transform")
    @Transactional(rollbackFor=Exception.class)
    public BaseResponse<PictureVO> transform(@RequestBody TransformRequest body,HttpServletRequest request)throws java.io.IOException{
        ThrowUtils.throwIf(body==null||body.getId()==null,ErrorCode.PARAMS_ERROR);
        User user=userService.getLoginUser(request);Picture p=pictureService.getOne(new QueryWrapper<Picture>().eq("id",body.getId()).last("FOR UPDATE"));
        checkPictureEditPermission(p,user);
        String key=p.getUrl().substring(p.getUrl().lastIndexOf('/')+1);byte[] original=fileManager.read(key);
        byte[] modified=fileManager.transform(original,body.getRotation()==null?90:body.getRotation(),Boolean.TRUE.equals(body.getFlip()),p.getPicFormat());
        return uploadPicture(new com.example.picturebackend.manager.ByteArrayMultipartFile(modified,p.getName()+"."+p.getPicFormat()),p.getId(),p.getSpaceId(),p.getName(),p.getIntroduction(),p.getCategory(),p.getTags(),request);
    }
    @lombok.Data public static class TransformRequest{private Long id;private Integer rotation;private Boolean flip;}

    private void checkPictureEditPermission(Picture picture, User user) {
        locks.check(picture.getId(),user.getId());
        if (picture.getSpaceId() != null) {
            spaceService.check(picture.getSpaceId(), user, "edit");
            return;
        }
        if (!user.getId().equals(picture.getUserId()) && !userService.isAdmin(user)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    private String normalizeTags(String tags) {
        if (!StringUtils.hasText(tags)) return "[]";
        try {
            List<String> normalized = JSONUtil.toList(tags, String.class).stream()
                    .filter(StringUtils::hasText).map(String::trim).distinct().limit(10).collect(Collectors.toList());
            return JSONUtil.toJsonStr(normalized);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签必须是 JSON 字符串数组");
        }
    }

    private String normalizeTags(List<String> tags){
        ThrowUtils.throwIf(tags.size()>10,ErrorCode.PARAMS_ERROR,"最多添加 10 个标签");
        List<String> values=tags.stream().filter(StringUtils::hasText).map(String::trim).distinct().collect(Collectors.toList());
        ThrowUtils.throwIf(values.stream().anyMatch(tag->tag.length()>32),ErrorCode.PARAMS_ERROR,"单个标签不能超过 32 字");
        return JSONUtil.toJsonStr(values);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
    private String newShareToken(){byte[] bytes=new byte[32];SHARE_RANDOM.nextBytes(bytes);StringBuilder out=new StringBuilder(64);for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}

    private void validateMetadata(Picture p) {
        ThrowUtils.throwIf(!StringUtils.hasText(p.getName()) || p.getName().length()>128
            || (p.getIntroduction()!=null && p.getIntroduction().length()>800)
            || (p.getCategory()!=null && p.getCategory().length()>64)
            || (p.getTags()!=null && p.getTags().length()>1024),ErrorCode.PARAMS_ERROR,"图片名称或说明、标签过长");
    }

    @PostMapping("/space/list/page/vo")
    public BaseResponse<Page<PictureVO>> spacePictures(@RequestBody PictureQueryRequest body,HttpServletRequest request){
        ThrowUtils.throwIf(body==null,ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(body.getSpaceId()==null,ErrorCode.PARAMS_ERROR);
        spaceService.check(body.getSpaceId(),userService.getLoginUser(request),"read");
        body.setIncludeAllSpaces(false);body.setReviewStatus(null);return listPage(body);
    }

    @PostMapping("/share/revoke")
    public BaseResponse<Boolean> revoke(@RequestBody DeleteRequest body,HttpServletRequest request){
        ThrowUtils.throwIf(body==null||body.getId()==null,ErrorCode.PARAMS_ERROR);
        Picture p=pictureService.getById(body.getId());access.edit(p,userService.getLoginUser(request));
        return ResultUtils.success(pictureService.update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Picture>()
            .eq("id",p.getId()).set("shareCode",null).set("shareExpireTime",null)));
    }

    @lombok.Data
    public static class PictureBatchEditRequest {
        private List<Long> ids;
        private String category;
        private List<String> tags;
        private String nameRule;
    }
}
