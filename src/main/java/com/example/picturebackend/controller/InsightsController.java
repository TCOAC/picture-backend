package com.example.picturebackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.picturebackend.common.*;
import com.example.picturebackend.domain.space.*;
import com.example.picturebackend.exception.*;
import com.example.picturebackend.model.entity.*;
import com.example.picturebackend.model.vo.PictureVO;
import com.example.picturebackend.model.tdo.picture.PictureColorSearchRequest;
import com.example.picturebackend.model.tdo.picture.PictureSimilarSearchRequest;
import com.example.picturebackend.mapper.SpaceMapper;
import cn.hutool.json.JSONUtil;
import com.example.picturebackend.service.*;
import lombok.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class InsightsController {
    private final PictureService pictures;private final UserService users;private final SpaceDomainService spaces;private final PictureAccess access;
    private final SpaceMapper spaceMapper;
    @Data public static class SimilarRequest {private Long spaceId;private Long pictureId;private String color;}
    private QueryWrapper<Picture> scope(Long spaceId,HttpServletRequest request){
        QueryWrapper<Picture> q=new QueryWrapper<>();
        if(spaceId==null)q.isNull("spaceId").eq("reviewStatus",1);
        else{spaces.check(spaceId,users.getLoginUser(request),"read");q.eq("spaceId",spaceId);}return q;
    }
    @PostMapping("/picture/search/similar")
    public BaseResponse<Map<String,Object>> similar(@RequestBody SimilarRequest body,HttpServletRequest request){
        ThrowUtils.throwIf(body == null, ErrorCode.PARAMS_ERROR, "请输入搜索条件");
        QueryWrapper<Picture> q=scope(body.getSpaceId(),request);
        final String hash;final int color;
        if(body.getPictureId()!=null){Picture p=pictures.getById(body.getPictureId());access.read(p,request,null);hash=p.getPerceptualHash();color=0;
            ThrowUtils.throwIf(hash==null,ErrorCode.PARAMS_ERROR,"请重新上传此图片以建立相似度索引");
        }else{ThrowUtils.throwIf(body.getColor()==null || !body.getColor().matches("#[0-9a-fA-F]{6}"),ErrorCode.PARAMS_ERROR,"请选择颜色");hash=null;color=Integer.parseInt(body.getColor().substring(1),16);}
        q.isNotNull(hash==null?"picColor":"perceptualHash");
        long count=pictures.count(q);List<Picture> candidates=pictures.list(q.orderByDesc("createTime").last("LIMIT 2000"));
        List<Picture> result=candidates.stream()
            .filter(p -> hash == null ? validColor(p.getPicColor()) : validHash(p.getPerceptualHash()))
            .filter(p->!p.getId().equals(body.getPictureId()))
            .sorted(Comparator.comparingDouble(p->hash!=null?Long.bitCount(Long.parseUnsignedLong(hash,16)^Long.parseUnsignedLong(p.getPerceptualHash(),16)):distance(color,Integer.parseInt(p.getPicColor().substring(1),16))))
            .limit(30).collect(Collectors.toList());
        Map<String,Object> data=new LinkedHashMap<>();data.put("records",pictures.getPictureVOList(result));data.put("searched",candidates.size());data.put("total",count);return ResultUtils.success(data);
    }

    @PostMapping("/picture/search/color")
    public BaseResponse<List<PictureVO>> colorSearch(@RequestBody PictureColorSearchRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getPicColor() == null || !body.getPicColor().matches("#[0-9a-fA-F]{6}"),
                ErrorCode.PARAMS_ERROR, "颜色格式不正确");
        QueryWrapper<Picture> q = scope(body.getSpaceId(), request).isNotNull("picColor");
        int color = Integer.parseInt(body.getPicColor().substring(1), 16);
        List<Picture> candidates = pictures.list(q.orderByDesc("createTime").last("LIMIT 2000"))
                .stream().filter(picture -> validColor(picture.getPicColor())).collect(Collectors.toList());
        candidates.sort(Comparator.comparingDouble(p -> distance(color, parseColor(p.getPicColor()))));
        return ResultUtils.success(pictures.getPictureVOList(candidates.stream().limit(30).collect(Collectors.toList())));
    }

    @PostMapping("/picture/search/picture")
    public BaseResponse<Map<String, Object>> pictureSearch(@RequestBody PictureSimilarSearchRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getPictureId() == null, ErrorCode.PARAMS_ERROR, "图片不能为空");
        SimilarRequest requestBody = new SimilarRequest();
        requestBody.setPictureId(body.getPictureId());
        requestBody.setSpaceId(body.getSpaceId());
        return similar(requestBody, request);
    }
    private boolean validColor(String value) { return value != null && value.matches("#[0-9a-fA-F]{6}"); }
    private boolean validHash(String value) { return value != null && value.matches("[0-9a-fA-F]{16}"); }
    private int parseColor(String value) { return Integer.parseInt(value.substring(1), 16); }

    private double distance(int a,int b){int r=(a>>16&255)-(b>>16&255),g=(a>>8&255)-(b>>8&255),bl=(a&255)-(b&255);return r*r+g*g+bl*bl;}
    @GetMapping("/space/analyze")
    public BaseResponse<Map<String,Object>> analyze(@RequestParam(required=false) Long spaceId,HttpServletRequest request){
        QueryWrapper<Picture> q;
        if(spaceId==null){ThrowUtils.throwIf(!users.isAdmin(users.getLoginUser(request)),ErrorCode.NO_AUTH_ERROR);q=new QueryWrapper<>();}
        else{spaces.check(spaceId,users.getLoginUser(request),"read");q=new QueryWrapper<Picture>().eq("spaceId",spaceId);}
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("summary",pictures.getBaseMapper().selectMaps(q.clone().select("COUNT(*) AS pictureCount","COALESCE(SUM(picSize),0) AS totalSize","COALESCE(SUM(viewCount),0) AS views","COALESCE(SUM(downloadCount),0) AS downloads")).get(0));
        data.put("categories",pictures.getBaseMapper().selectMaps(q.clone().select("category AS label","COUNT(*) AS count","SUM(picSize) AS size").groupBy("category")));
        data.put("formats",pictures.getBaseMapper().selectMaps(q.clone().select("picFormat AS label","COUNT(*) AS count").groupBy("picFormat")));
        data.put("contributors",pictures.getBaseMapper().selectMaps(q.clone().select("userId AS label","COUNT(*) AS count").groupBy("userId").orderByDesc("count").last("LIMIT 10")));
        data.put("daily",pictures.getBaseMapper().selectMaps(q.clone().select("CAST(createTime AS DATE) AS label","COUNT(*) AS count").groupBy("CAST(createTime AS DATE)").orderByDesc("label").last("LIMIT 30")));
        List<Picture> samples = pictures.list(q.clone().select("tags", "picSize").last("LIMIT 5000"));
        Map<String, Long> tagCounts = new HashMap<>();
        Map<String, Long> sizeCounts = new LinkedHashMap<>();
        sizeCounts.put("100 KB 以下", 0L);
        sizeCounts.put("100 KB - 1 MB", 0L);
        sizeCounts.put("1 MB - 5 MB", 0L);
        sizeCounts.put("5 MB 以上", 0L);
        for (Picture picture : samples) {
            if (picture.getTags() != null) {
                try {
                    for (String tag : JSONUtil.toList(picture.getTags(), String.class)) {
                        if (tag != null && !tag.trim().isEmpty()) tagCounts.merge(tag.trim(), 1L, Long::sum);
                    }
                } catch (Exception ignored) { }
            }
            long size = picture.getPicSize() == null ? 0 : picture.getPicSize();
            String bucket = size < 100 * 1024 ? "100 KB 以下" : size < 1024 * 1024 ? "100 KB - 1 MB"
                    : size < 5 * 1024 * 1024 ? "1 MB - 5 MB" : "5 MB 以上";
            sizeCounts.merge(bucket, 1L, Long::sum);
        }
        data.put("tags", tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(20)
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("label", entry.getKey());
                    item.put("count", entry.getValue());
                    return item;
                }).collect(Collectors.toList()));
        data.put("sizes", sizeCounts.entrySet().stream().map(entry -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", entry.getKey());
            item.put("count", entry.getValue());
            return item;
        }).collect(Collectors.toList()));
        data.put("analyzedPictures", samples.size());
        if (spaceId == null) data.put("spaceRank", spaceMapper.selectList(new QueryWrapper<Space>()
                .orderByDesc("totalSize").last("LIMIT 10")));
        if(spaceId!=null)data.put("space",spaces.require(spaceId));return ResultUtils.success(data);
    }
}
