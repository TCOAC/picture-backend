package com.example.picturebackend.model.vo;

import com.example.picturebackend.model.entity.SpaceUser;
import com.example.picturebackend.model.entity.User;
import lombok.Getter;

import java.util.Date;

@Getter
public class SpaceMemberVO {
    private final Long id;
    private final Long userId;
    private final String spaceRole;
    private final Date createTime;
    private final String userName;
    private final String userAvatar;

    public SpaceMemberVO(SpaceUser member, User user) {
        id = member.getId();
        userId = member.getUserId();
        spaceRole = member.getSpaceRole();
        createTime = member.getCreateTime();
        userName = user == null ? null : user.getUserName();
        userAvatar = user == null ? null : user.getUserAvatar();
    }
}
