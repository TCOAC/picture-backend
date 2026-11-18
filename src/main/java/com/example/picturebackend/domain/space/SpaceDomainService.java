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
        boolean allowed = role != null && ("read".equals(permission) || "admin".equals(role)
                || ("edit".equals(permission) && "editor".equals(role)));
        ThrowUtils.throwIf(!allowed, ErrorCode.NO_AUTH_ERROR, "无空间操作权限");
    }

    @Transactional
    public Space create(String name, int type, User user) {
        ThrowUtils.throwIf(name == null || name.trim().isEmpty() || name.length() > 128,
                ErrorCode.PARAMS_ERROR, "空间名称长度为 1-128");
        ThrowUtils.throwIf(type != 0 && type != 1, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(user==null,ErrorCode.NOT_LOGIN_ERROR);
        if(type==0)ThrowUtils.throwIf(spaces.selectCount(new QueryWrapper<Space>().eq("userId",user.getId()).eq("spaceType",0))>0,
            ErrorCode.OPERATION_ERROR,"每位用户只能创建一个个人空间");
        ThrowUtils.throwIf(spaces.selectCount(new QueryWrapper<Space>().eq("userId",user.getId()))>=20,
            ErrorCode.OPERATION_ERROR,"每人最多创建 20 个空间");
        Space space = new Space();
        space.setSpaceName(name.trim()); space.setSpaceType(type); space.setSpaceLevel(0);
        space.setUserId(user.getId()); space.setMaxCount(100L); space.setMaxSize(100L * 1024 * 1024);
        space.setTotalCount(0L); space.setTotalSize(0L);
        spaces.insert(space);
        if (type == 1) {
            SpaceUser member = new SpaceUser();
            member.setSpaceId(space.getId()); member.setUserId(user.getId()); member.setSpaceRole("admin");
            members.insert(member);
        }
        return space;
    }

    public List<Space> mine(User user) {
        Set<Long> ids = new HashSet<>();
        members.selectList(new QueryWrapper<SpaceUser>().eq("userId", user.getId()))
                .forEach(m -> ids.add(m.getSpaceId()));
        return spaces.selectList(new QueryWrapper<Space>().and(q -> {
            q.eq("userId", user.getId());
            if (!ids.isEmpty()) q.or().in("id", ids);
        }).orderByDesc("createTime"));
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
        ThrowUtils.throwIf(require(id).getUserId().equals(userId), ErrorCode.PARAMS_ERROR, "不能移除空间创建者");
        members.delete(new QueryWrapper<SpaceUser>().eq("spaceId", id).eq("userId", userId));
    }
}
