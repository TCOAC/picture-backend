package com.example.picturebackend.service;

import com.example.picturebackend.exception.BusinessException;
import com.example.picturebackend.exception.ErrorCode;
import com.example.picturebackend.mapper.UserMapper;
import com.example.picturebackend.mapper.VipCodeMapper;
import com.example.picturebackend.model.entity.User;
import com.example.picturebackend.model.entity.VipCode;
import com.example.picturebackend.model.vo.LoginUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class VipCodeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private final VipCodeMapper codes;
    private final UserMapper users;
    private final UserService userService;

    public String issue(int durationDays, Long adminId) {
        if (durationDays < 1 || durationDays > 365) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会员天数须在 1 到 365 之间");
        }
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        String code = "VIP-" + hex(random);
        VipCode record = new VipCode();
        record.setCodeHash(hash(code));
        record.setDurationDays(durationDays);
        record.setCreatedBy(adminId);
        if (codes.insert(record) != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成兑换码失败");
        }
        return code;
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginUserVO redeem(String rawCode, Long userId) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (!code.matches("VIP-[0-9A-F]{32}")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "兑换码格式不正确");
        }

        // Lock the user first so two different codes redeemed concurrently extend the same expiry.
        User user = users.lockById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        String codeHash = hash(code);
        VipCode record = codes.selectById(codeHash);
        if (record == null || codes.claim(codeHash, userId) != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "兑换码无效或已使用");
        }

        Instant now = Instant.now();
        Instant base = user.getVipExpireTime() != null && user.getVipExpireTime().toInstant().isAfter(now)
                ? user.getVipExpireTime().toInstant() : now;
        user.setVipExpireTime(Date.from(base.plus(record.getDurationDays(), ChronoUnit.DAYS)));
        if (users.updateById(user) != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新会员信息失败");
        }
        return userService.getLoginUserVO(user);
    }

    private static String hash(String code) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
            result[i * 2 + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(result);
    }
}
