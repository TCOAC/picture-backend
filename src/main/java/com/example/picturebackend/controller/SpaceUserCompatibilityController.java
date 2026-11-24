package com.example.picturebackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.picturebackend.common.BaseResponse;
import com.example.picturebackend.common.ResultUtils;
import com.example.picturebackend.domain.space.SpaceDomainService;
import com.example.picturebackend.exception.ErrorCode;
import com.example.picturebackend.exception.ThrowUtils;
import com.example.picturebackend.mapper.SpaceUserMapper;
import com.example.picturebackend.model.entity.SpaceUser;
import com.example.picturebackend.model.entity.User;
import com.example.picturebackend.model.vo.SpaceMemberVO;
import com.example.picturebackend.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/spaceUser")
public class SpaceUserCompatibilityController {
    private final SpaceDomainService spaces;
    private final SpaceUserMapper members;
    private final UserService users;

    public SpaceUserCompatibilityController(SpaceDomainService spaces, SpaceUserMapper members, UserService users) {
        this.spaces = spaces;
        this.members = members;
        this.users = users;
    }

    @PostMapping("/add")
    public BaseResponse<Boolean> add(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return save(body, request);
    }

    @PostMapping("/edit")
    public BaseResponse<Boolean> edit(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return save(body, request);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> delete(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long spaceId = id(body.get("spaceId"));
        Long userId = id(body.get("userId"));
        ThrowUtils.throwIf(spaceId == null || userId == null, ErrorCode.PARAMS_ERROR);
        spaces.removeMember(spaceId, userId, users.getLoginUser(request));
        return ResultUtils.success(true);
    }

    @PostMapping("/list")
    public BaseResponse<List<SpaceMemberVO>> list(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long spaceId = id(body == null ? null : body.get("spaceId"));
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
        List<SpaceUser> records = spaces.members(spaceId, users.getLoginUser(request));
        Map<Long, User> byId = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        records.forEach(record -> ids.add(record.getUserId()));
        if (!ids.isEmpty()) users.listByIds(ids).forEach(user -> byId.put(user.getId(), user));
        List<SpaceMemberVO> result = new ArrayList<>();
        records.forEach(record -> result.add(new SpaceMemberVO(record, byId.get(record.getUserId()))));
        return ResultUtils.success(result);
    }

    private BaseResponse<Boolean> save(Map<String, Object> body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = id(body.get("spaceId"));
        Long userId = id(body.get("userId"));
        String role = body.get("spaceRole") == null ? "viewer" : String.valueOf(body.get("spaceRole"));
        ThrowUtils.throwIf(spaceId == null || userId == null, ErrorCode.PARAMS_ERROR);
        spaces.setMember(spaceId, userId, role, users.getLoginUser(request));
        return ResultUtils.success(true);
    }

    private static Long id(Object value) {
        if (value == null) return null;
        return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value));
    }
}
