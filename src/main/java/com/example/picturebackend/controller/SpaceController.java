package com.example.picturebackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.picturebackend.common.*;
import com.example.picturebackend.domain.space.SpaceDomainService;
import com.example.picturebackend.exception.*;
import com.example.picturebackend.mapper.SpaceMapper;
import com.example.picturebackend.mapper.SpaceUserMapper;
import com.example.picturebackend.model.entity.*;
import com.example.picturebackend.model.vo.SpaceMemberVO;
import com.example.picturebackend.service.UserService;
import lombok.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/space")
@RequiredArgsConstructor
public class SpaceController {
    private final SpaceDomainService spaces;
    private final SpaceMapper mapper;
    private final SpaceUserMapper memberMapper;
    private final UserService users;

    @Data public static class SpaceRequest {
        private Long id; private String spaceName; private Integer spaceType; private Integer spaceLevel;
        private Long userId; private String userAccount; private String spaceRole;
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
            space.setSpaceLevel(level); space.setMaxSize((level==0?100L:level==1?1000L:10000L)*1024*1024);
            space.setMaxCount(level==0?100L:level==1?1000L:10000L);
            ThrowUtils.throwIf(space.getTotalSize()>space.getMaxSize() || space.getTotalCount()>space.getMaxCount(), ErrorCode.PARAMS_ERROR, "新配额小于已用容量");
        }
        return ResultUtils.success(mapper.updateById(space)==1);
    }

    @PostMapping("/delete")
    @Transactional(rollbackFor=Exception.class)
    public BaseResponse<Boolean> delete(@RequestBody DeleteRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body==null||body.getId()==null,ErrorCode.PARAMS_ERROR);
        spaces.check(body.getId(), users.getLoginUser(request), "admin");
        ThrowUtils.throwIf(spaces.require(body.getId()).getTotalCount()!=0, ErrorCode.OPERATION_ERROR, "请先清空空间");
        ThrowUtils.throwIf(mapper.deleteById(body.getId()) != 1, ErrorCode.OPERATION_ERROR);
        memberMapper.delete(new QueryWrapper<SpaceUser>().eq("spaceId", body.getId()));
        return ResultUtils.success(true);
    }

    @GetMapping("/members")
    public BaseResponse<List<SpaceMemberVO>> members(@RequestParam Long id, HttpServletRequest request) {
        List<SpaceUser> members = spaces.members(id, users.getLoginUser(request));
        Set<Long> userIds = new HashSet<>();
        members.forEach(member -> userIds.add(member.getUserId()));
        Map<Long, User> byId = new HashMap<>();
        if (!userIds.isEmpty()) users.listByIds(userIds).forEach(user -> byId.put(user.getId(), user));
        List<SpaceMemberVO> result = new ArrayList<>();
        for (SpaceUser member : members) {
            User user = byId.get(member.getUserId());
            result.add(new SpaceMemberVO(member, user));
        }
        return ResultUtils.success(result);
    }

    @PostMapping("/member")
    public BaseResponse<Boolean> member(@RequestBody SpaceRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = users.getLoginUser(request);
        spaces.check(body.getId(), loginUser, "admin");
        User member = body.getUserId() != null ? users.getById(body.getUserId()) : null;
        if (member == null && StringUtils.hasText(body.getUserAccount())) {
            member = users.getOne(new QueryWrapper<User>().eq("userAccount", body.getUserAccount().trim()));
        }
        ThrowUtils.throwIf(member == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        spaces.setMember(body.getId(),member.getId(),body.getSpaceRole(),loginUser);
        return ResultUtils.success(true);
    }

    @PostMapping("/member/delete")
    public BaseResponse<Boolean> removeMember(@RequestBody SpaceRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body==null||body.getId()==null||body.getUserId()==null,ErrorCode.PARAMS_ERROR);
        spaces.removeMember(body.getId(),body.getUserId(),users.getLoginUser(request)); return ResultUtils.success(true);
    }
}
