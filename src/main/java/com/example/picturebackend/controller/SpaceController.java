package com.example.picturebackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.picturebackend.common.*;
import com.example.picturebackend.domain.space.SpaceDomainService;
import com.example.picturebackend.exception.*;
import com.example.picturebackend.mapper.SpaceMapper;
import com.example.picturebackend.model.entity.*;
import com.example.picturebackend.service.UserService;
import lombok.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/space")
@RequiredArgsConstructor
public class SpaceController {
    private final SpaceDomainService spaces;
    private final SpaceMapper mapper;
    private final UserService users;

    @Data public static class SpaceRequest {
        private Long id; private String spaceName; private Integer spaceType; private Integer spaceLevel;
        private Long userId; private String spaceRole;
    }

    @PostMapping("/add")
    public BaseResponse<Space> create(@RequestBody SpaceRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body==null,ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(spaces.create(body.getSpaceName(), body.getSpaceType() == null ? 0 : body.getSpaceType(), users.getLoginUser(request)));
    }

    @GetMapping("/mine")
    public BaseResponse<List<Space>> mine(HttpServletRequest request) {
        return ResultUtils.success(spaces.mine(users.getLoginUser(request)));
    }

    @GetMapping("/get/vo")
    public BaseResponse<Map<String,Object>> get(@RequestParam Long id, HttpServletRequest request) {
        User user = users.getLoginUser(request); spaces.check(id, user, "read");
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("space", spaces.require(id)); result.put("role", spaces.role(id, user));
        return ResultUtils.success(result);
    }

    @GetMapping("/list")
    public BaseResponse<List<Space>> list(HttpServletRequest request) {
        ThrowUtils.throwIf(!spaces.isAdmin(users.getLoginUser(request)), ErrorCode.NO_AUTH_ERROR);
        return ResultUtils.success(mapper.selectList(new QueryWrapper<Space>().orderByDesc("createTime").last("LIMIT 200")));
    }

    @PostMapping("/edit")
    @Transactional(rollbackFor=Exception.class)
    public BaseResponse<Boolean> edit(@RequestBody SpaceRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body==null||body.getId()==null,ErrorCode.PARAMS_ERROR);
        User user = users.getLoginUser(request); spaces.check(body.getId(), user, "admin");
        Space space = spaces.require(body.getId());
        if (body.getSpaceName() != null) {
            ThrowUtils.throwIf(body.getSpaceName().trim().isEmpty() || body.getSpaceName().length()>128, ErrorCode.PARAMS_ERROR);
            space.setSpaceName(body.getSpaceName().trim());
        }
        if (body.getSpaceLevel() != null) {
            ThrowUtils.throwIf(!spaces.isAdmin(user), ErrorCode.NO_AUTH_ERROR, "空间等级由管理员配置");
            int level=body.getSpaceLevel(); ThrowUtils.throwIf(level<0 || level>2, ErrorCode.PARAMS_ERROR);
            space.setSpaceLevel(level); space.setMaxSize((level==0?100L:level==1?1024L:10240L)*1024*1024);
            space.setMaxCount(level==0?100L:level==1?1000L:10000L);
            ThrowUtils.throwIf(space.getTotalSize()>space.getMaxSize() || space.getTotalCount()>space.getMaxCount(), ErrorCode.PARAMS_ERROR, "新配额小于已用容量");
        }
        return ResultUtils.success(mapper.updateById(space)==1);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> delete(@RequestBody DeleteRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body==null||body.getId()==null,ErrorCode.PARAMS_ERROR);
        spaces.check(body.getId(), users.getLoginUser(request), "admin");
        ThrowUtils.throwIf(spaces.require(body.getId()).getTotalCount()!=0, ErrorCode.OPERATION_ERROR, "请先清空空间");
        return ResultUtils.success(mapper.deleteById(body.getId())==1);
    }

    @GetMapping("/members")
    public BaseResponse<List<SpaceUser>> members(@RequestParam Long id, HttpServletRequest request) {
        return ResultUtils.success(spaces.members(id, users.getLoginUser(request)));
    }

    @PostMapping("/member")
    public BaseResponse<Boolean> member(@RequestBody SpaceRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body.getUserId()==null || users.getById(body.getUserId())==null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        spaces.setMember(body.getId(),body.getUserId(),body.getSpaceRole(),users.getLoginUser(request));
        return ResultUtils.success(true);
    }

    @PostMapping("/member/delete")
    public BaseResponse<Boolean> removeMember(@RequestBody SpaceRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body==null||body.getId()==null||body.getUserId()==null,ErrorCode.PARAMS_ERROR);
        spaces.removeMember(body.getId(),body.getUserId(),users.getLoginUser(request)); return ResultUtils.success(true);
    }
}
