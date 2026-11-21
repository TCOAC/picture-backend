package com.example.picturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.picturebackend.model.tdo.picture.PictureQueryRequest;
import com.example.picturebackend.model.vo.PictureVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class PublicGalleryCache {
    private static final String VERSION_KEY = "gallery:public:version";
    private static final int TTL_SECONDS = 30;

    private final ObjectMapper json;
    private final AtomicLong localVersion = new AtomicLong();
    private final Cache<String, CachedPage> local = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(TTL_SECONDS, TimeUnit.SECONDS)
            .build();

    @Autowired(required = false)
    private StringRedisTemplate redis;

    @Value("${app.cache.redis-enabled:false}")
    private boolean redisEnabled;

    public Page<PictureVO> get(PictureQueryRequest request, Supplier<Page<PictureVO>> loader) {
        String key = cacheKey(request);
        CachedPage page = local.getIfPresent(key);
        if (page == null) page = loadRemote(key);
        if (page == null) {
            page = CachedPage.from(loader.get());
            storeRemote(key, page);
        }
        local.put(key, page);
        return page.toPage();
    }

    public void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate();
                }
            });
        } else {
            invalidate();
        }
    }

    private void invalidate() {
        localVersion.incrementAndGet();
        local.invalidateAll();
        if (redisEnabled && redis != null) {
            try {
                redis.opsForValue().increment(VERSION_KEY);
            } catch (Exception ignored) {
                // Local caching stays available if Redis is temporarily unavailable.
            }
        }
    }

    private String cacheKey(PictureQueryRequest request) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(json.writeValueAsBytes(request));
            StringBuilder hash = new StringBuilder(digest.length * 2);
            for (byte value : digest) hash.append(String.format("%02x", value & 0xff));
            return "gallery:public:" + localVersion.get() + ':' + remoteVersion() + ':' + hash;
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成图库缓存键", exception);
        }
    }

    private String remoteVersion() {
        if (!redisEnabled || redis == null) return "0";
        try {
            String version = redis.opsForValue().get(VERSION_KEY);
            return version == null ? "0" : version;
        } catch (Exception ignored) {
            return "0";
        }
    }

    private CachedPage loadRemote(String key) {
        if (!redisEnabled || redis == null) return null;
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : json.readValue(value, CachedPage.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void storeRemote(String key, CachedPage page) {
        if (!redisEnabled || redis == null) return;
        try {
            redis.opsForValue().set(key, json.writeValueAsString(page), TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Requests still succeed when the shared cache is unavailable.
        }
    }

    @Data
    public static class CachedPage {
        private long current;
        private long pageSize;
        private long total;
        private List<PictureVO> records;

        static CachedPage from(Page<PictureVO> page) {
            CachedPage copy = new CachedPage();
            copy.current = page.getCurrent();
            copy.pageSize = page.getSize();
            copy.total = page.getTotal();
            copy.records = page.getRecords();
            return copy;
        }

        Page<PictureVO> toPage() {
            Page<PictureVO> result = new Page<>(current, pageSize, total);
            result.setRecords(records);
            return result;
        }
    }
}
