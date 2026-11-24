package com.example.picturebackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.picturebackend.common.BaseResponse;
import com.example.picturebackend.common.ResultUtils;
import com.example.picturebackend.domain.space.SpaceDomainService;
import com.example.picturebackend.exception.ErrorCode;
import com.example.picturebackend.exception.ThrowUtils;
import com.example.picturebackend.mapper.PictureMapper;
import com.example.picturebackend.mapper.SpaceMapper;
import com.example.picturebackend.model.entity.Picture;
import com.example.picturebackend.model.entity.Space;
import com.example.picturebackend.model.entity.User;
import com.example.picturebackend.service.UserService;
import cn.hutool.json.JSONUtil;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/space/analyze")
public class SpaceAnalyzeController {
    private final PictureMapper pictures;
    private final SpaceMapper spaces;
    private final SpaceDomainService auth;
    private final UserService users;

    public SpaceAnalyzeController(PictureMapper pictures, SpaceMapper spaces,
                                  SpaceDomainService auth, UserService users) {
        this.pictures = pictures;
        this.spaces = spaces;
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/usage")
    public BaseResponse<Map<String, Object>> usage(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        QueryWrapper<Picture> query = scope(body, request);
        Map<String, Object> result = new LinkedHashMap<>();
        if (queryScope(body).equals("space")) {
            Space space = auth.require(longValue(body.get("spaceId")));
            result.put("usedSize", space.getTotalSize());
            result.put("usedCount", space.getTotalCount());
            result.put("maxSize", space.getMaxSize());
            result.put("maxCount", space.getMaxCount());
            result.put("sizeUsageRatio", ratio(space.getTotalSize(), space.getMaxSize()));
            result.put("countUsageRatio", ratio(space.getTotalCount(), space.getMaxCount()));
        } else {
            List<Map<String, Object>> rows = pictures.selectMaps(query.select("picSize"));
            long size = rows.stream().mapToLong(row -> number(row.get("picSize"))).sum();
            result.put("usedSize", size);
            result.put("usedCount", rows.size());
            result.put("maxSize", null);
            result.put("maxCount", null);
            result.put("sizeUsageRatio", null);
            result.put("countUsageRatio", null);
        }
        return ResultUtils.success(result);
    }

    @PostMapping("/category")
    public BaseResponse<List<Map<String, Object>>> category(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        QueryWrapper<Picture> query = scope(body, request)
                .select("category AS category", "COUNT(*) AS count", "COALESCE(SUM(picSize),0) AS totalSize")
                .groupBy("category");
        return ResultUtils.success(pictures.selectMaps(query));
    }

    @PostMapping("/tag")
    public BaseResponse<List<Map<String, Object>>> tag(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        List<Picture> rows = pictures.selectList(scope(body, request).select("tags"));
        Map<String, Long> counts = new HashMap<>();
        for (Picture picture : rows) {
            if (picture.getTags() == null) continue;
            try {
                for (String tag : JSONUtil.toList(picture.getTags(), String.class)) {
                    if (tag != null && !tag.trim().isEmpty()) counts.merge(tag.trim(), 1L, Long::sum);
                }
            } catch (Exception ignored) { }
        }
        List<Map<String, Object>> result = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("tag", entry.getKey());
                    value.put("count", entry.getValue());
                    return value;
                }).collect(Collectors.toList());
        return ResultUtils.success(result);
    }

    @PostMapping("/size")
    public BaseResponse<List<Map<String, Object>>> size(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        List<Picture> rows = pictures.selectList(scope(body, request).select("picSize"));
        long small = rows.stream().filter(row -> number(row.getPicSize()) < 100 * 1024).count();
        long medium = rows.stream().filter(row -> number(row.getPicSize()) >= 100 * 1024 && number(row.getPicSize()) < 500 * 1024).count();
        long large = rows.stream().filter(row -> number(row.getPicSize()) >= 500 * 1024 && number(row.getPicSize()) < 1024 * 1024).count();
        long huge = rows.stream().filter(row -> number(row.getPicSize()) >= 1024 * 1024).count();
        return ResultUtils.success(Arrays.asList(bucket("<100KB", small), bucket("100KB-500KB", medium),
                bucket("500KB-1MB", large), bucket(">1MB", huge)));
    }

    @PostMapping("/user")
    public BaseResponse<List<Map<String, Object>>> user(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String dimension = String.valueOf(body.getOrDefault("timeDimension", "day"));
        String expression = "month".equals(dimension) ? "CAST(createTime AS DATE)" :
                "week".equals(dimension) ? "WEEK(createTime)" : "CAST(createTime AS DATE)";
        QueryWrapper<Picture> query = scope(body, request)
                .select(expression + " AS period", "COUNT(*) AS count")
                .groupBy(expression).orderByAsc("period");
        return ResultUtils.success(pictures.selectMaps(query));
    }

    @PostMapping("/rank")
    public BaseResponse<List<Space>> rank(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User user = users.getLoginUser(request);
        ThrowUtils.throwIf(!users.isAdmin(user), ErrorCode.NO_AUTH_ERROR);
        int topN = Math.max(1, Math.min(100, intValue(body.getOrDefault("topN", 10))));
        return ResultUtils.success(spaces.selectList(new QueryWrapper<Space>()
                .orderByDesc("totalSize").last("LIMIT " + topN)));
    }

    private QueryWrapper<Picture> scope(Map<String, Object> body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null, ErrorCode.PARAMS_ERROR);
        String target = queryScope(body);
        if ("all".equals(target) || "public".equals(target)) {
            ThrowUtils.throwIf(!users.isAdmin(users.getLoginUser(request)), ErrorCode.NO_AUTH_ERROR);
            QueryWrapper<Picture> query = new QueryWrapper<>();
            if ("public".equals(target)) query.isNull("spaceId").eq("reviewStatus", 1);
            return query;
        }
        Long spaceId = longValue(body.get("spaceId"));
        auth.check(spaceId, users.getLoginUser(request), "read");
        return new QueryWrapper<Picture>().eq("spaceId", spaceId);
    }

    private String queryScope(Map<String, Object> body) {
        if (Boolean.TRUE.equals(body.get("queryAll"))) return "all";
        if (Boolean.TRUE.equals(body.get("queryPublic"))) return "public";
        ThrowUtils.throwIf(body == null || body.get("spaceId") == null, ErrorCode.PARAMS_ERROR);
        return "space";
    }

    private static long longValue(Object value) { return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value)); }
    private static int intValue(Object value) { return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value)); }
    private static long number(Object value) { return value == null ? 0 : ((Number) value).longValue(); }
    private static double ratio(long value, long max) { return max == 0 ? 0 : Math.round(value * 10000.0 / max) / 100.0; }
    private static Map<String, Object> bucket(String label, long count) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("sizeRange", label);
        value.put("count", count);
        return value;
    }
}

