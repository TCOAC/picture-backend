package com.example.picturebackend.service;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;

/** Sa-Token adapter kept behind a small application service. */
@Service
public class SaTokenAuthService {
    public String login(Long userId) {
        if (userId == null) {
            return null;
        }
        StpUtil.login(userId);
        return StpUtil.getTokenValue();
    }

    public Long resolve() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public void logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
    }
}
