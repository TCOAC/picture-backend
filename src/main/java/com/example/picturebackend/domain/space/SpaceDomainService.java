package com.example.picturebackend.domain.space;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.picturebackend.exception.*;
import com.example.picturebackend.mapper.*;
import com.example.picturebackend.model.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Space aggregate: membership, authorization and storage invariants. */
@Service
@RequiredArgsConstructor
public class SpaceDomainService {
    private final SpaceMapper spaces;
    private final SpaceUserMapper members;
    private final UserMapper users;

    public boolean isAdmin(User user) {
        return user != null && "admin".equals(user.getUserRole());
    }

    public Space require(Long id) {
        Space space = id == null ? null : spaces.selectById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        return space;
    }

    public String role(Long spaceId, User user) {
        Space space = require(spaceId);
        if (user == null) return null;
        if (isAdmin(user) || space.getUserId().equals(user.getId())) return "admin";
        if (!Integer.valueOf(1).equals(space.getSpaceType())) return null;
        SpaceUser member = members.selectOne(new QueryWrapper<SpaceUser>()
                .eq("spaceId", spaceId).eq("userId", user.getId()));
        return member == null ? null : member.getSpaceRole();
    }

    public void check(Long spaceId, User user, String permission) {
        if (spaceId == null) return;
        String role = role(spaceId, user);
        boolean read = "read".equals(permission) || "view".equals(permission);
        boolean edit = "edit".equals(permission) || "upload".equals(permission)
                || "delete".equals(permission);
        boolean manage = "admin".equals(permission) || "manage".equals(permission);
        boolean allowed = role != null && (read || (edit && ("editor".equals(role) || "admin".equals(role)))
                || (manage && "admin".equals(role)));
        ThrowUtils.throwIf(!allowed, ErrorCode.NO_AUTH_ERROR, "无空间操作权限");
    }

    @Transactional(rollbackFor = Exception.class)
    public Space create(String name, int type, User user) {
        ThrowUtils.throwIf(name == null || name.trim().isEmpty() || name.length() > 128,
                ErrorCode.PARAMS_ERROR, "空间名称长度为 1-128");
        ThrowUtils.throwIf(type != 0 && type != 1, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(user == null || user.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(users.lockById(user.getId()) == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(spaces.selectCount(new QueryWrapper<Space>()
                        .eq("userId", user.getId()).eq("spaceType", type)) > 0,
                ErrorCode.OPERATION_ERROR, "每位用户每类空间只能创建一个");
        Space space = new Space();
        space.setSpaceName(name.trim()); space.setSpaceType(type); space.setSpaceLevel(0);
        space.setUserId(user.getId()); space.setMaxCount(100L); space.setMaxSize(100L * 1024 * 1024);
        space.setTotalCount(0L); space.setTotalSize(0L);
        ThrowUtils.throwIf(spaces.insert(space) != 1, ErrorCode.OPERATION_ERROR, "创建空间失败");
        if (type == 1) {
            SpaceUser member = new SpaceUser();
            member.setSpaceId(space.getId()); member.setUserId(user.getId()); member.setSpaceRole("admin");
            ThrowUtils.throwIf(members.insert(member) != 1, ErrorCode.OPERATION_ERROR, "创建团队成员失败");
        }
        return space;
    }

    public List<Space> mine(User user) {
        Map<Long, String> memberships = new HashMap<>();
        members.selectList(new QueryWrapper<SpaceUser>().eq("userId", user.getId()))
                .forEach(member -> memberships.put(member.getSpaceId(), member.getSpaceRole()));
        List<Space> result = spaces.selectList(new QueryWrapper<Space>().and(q -> {
            q.eq("userId", user.getId());
            if (!memberships.isEmpty()) q.or().in("id", memberships.keySet());
        }).orderByDesc("createTime"));
        result.forEach(space -> space.setRole(space.getUserId().equals(user.getId())
                ? "admin" : memberships.get(space.getId())));
        return result;
    }

    public void adjustUsage(Long id, long count, long size) {
        if (id == null) return;
        ThrowUtils.throwIf(spaces.adjustUsage(id, count, size) != 1,
                ErrorCode.OPERATION_ERROR, "空间容量或数量限额不足，请清理空间");
    }

    public List<SpaceUser> members(Long id, User user) {
        check(id, user, "read");
        return members.selectList(new QueryWrapper<SpaceUser>().eq("spaceId", id));
    }

    @Transactional
    public void setMember(Long id, Long userId, String role, User user) {
        check(id, user, "admin");
        Space space = require(id);
        ThrowUtils.throwIf(!Integer.valueOf(1).equals(space.getSpaceType()), ErrorCode.PARAMS_ERROR, "仅团队空间可添加成员");
        ThrowUtils.throwIf(space.getUserId().equals(userId), ErrorCode.PARAMS_ERROR, "不能变更空间创建者权限");
        ThrowUtils.throwIf(!Arrays.asList("viewer", "editor", "admin").contains(role), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf("admin".equals(role) && !isAdmin(user) && !space.getUserId().equals(user.getId()),
                ErrorCode.NO_AUTH_ERROR, "只有空间创建者可以授予管理权限");
        SpaceUser member = members.selectOne(new QueryWrapper<SpaceUser>().eq("spaceId", id).eq("userId", userId));
        ThrowUtils.throwIf(member==null && members.selectCount(new QueryWrapper<SpaceUser>().eq("spaceId",id))>=50,
            ErrorCode.OPERATION_ERROR,"团队最多 50 名成员");
        if (member == null) {
            member = new SpaceUser(); member.setSpaceId(id); member.setUserId(userId); member.setSpaceRole(role);
            members.insert(member);
        } else { member.setSpaceRole(role); members.updateById(member); }
    }

    public void removeMember(Long id, Long userId, User user) {
        check(id, user, "admin");
        Space space = require(id);
        ThrowUtils.throwIf(space.getUserId().equals(userId), ErrorCode.PARAMS_ERROR, "不能移除空间创建者");
        SpaceUser target = members.selectOne(new QueryWrapper<SpaceUser>().eq("spaceId", id).eq("userId", userId));
        ThrowUtils.throwIf(target != null && "admin".equals(target.getSpaceRole())
                        && !isAdmin(user) && !space.getUserId().equals(user.getId()),
                ErrorCode.NO_AUTH_ERROR, "只有空间创建者可以移除管理员");
        members.delete(new QueryWrapper<SpaceUser>().eq("spaceId", id).eq("userId", userId));
    }

    @Transactional
    public void leave(Long id, User user) {
        Space space = require(id);
        ThrowUtils.throwIf(space.getUserId().equals(user.getId()), ErrorCode.PARAMS_ERROR, "空间创建者不能退出空间");
        ThrowUtils.throwIf(members.delete(new QueryWrapper<SpaceUser>().eq("spaceId", id).eq("userId", user.getId())) < 1,
                ErrorCode.NOT_FOUND_ERROR, "你不是该空间成员");
    }
}
