package com.example.picturebackend.service;

import com.example.picturebackend.exception.BusinessException;
import com.example.picturebackend.exception.ErrorCode;
import com.example.picturebackend.model.vo.ExternalImageSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ExternalImageSearchService {
    private static final Pattern FIRST_URL = Pattern.compile("\"firstUrl\"\\s*:\\s*\"(.*?)\"");
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.image-search.enabled:false}")
    private boolean enabled;
    @Value("${app.image-search.public-base-url:}")
    private String publicBaseUrl;
    @Value("${app.image-search.upload-endpoint:https://graph.baidu.com/upload}")
    private String uploadEndpoint;

    public List<ExternalImageSearchResult> search(String pictureUrl) {
        if (!enabled) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "外部以图搜图未配置");
        }
        String imageUrl = toPublicUrl(pictureUrl);
        String pageUrl = createSearchPage(imageUrl);
        return readResults(findResultEndpoint(pageUrl));
    }

    String toPublicUrl(String pictureUrl) {
        if (pictureUrl == null || pictureUrl.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址不能为空");
        }
        String value = pictureUrl.trim();
        if (value.startsWith("/")) {
            if (publicBaseUrl == null || publicBaseUrl.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "请配置图片公网地址后再使用外部搜索");
            }
            value = publicBaseUrl.replaceAll("/+$", "") + value;
        }
        validateHttpUrl(value);
        return value;
    }

    private String createSearchPage(String imageUrl) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("image", imageUrl);
            form.add("tn", "pc");
            form.add("from", "pc");
            form.add("image_source", "PC_UPLOAD_URL");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    uploadEndpoint + "?uptime=" + System.currentTimeMillis(),
                    new HttpEntity<>(form, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "外部搜索服务调用失败");
            }
            JsonNode root = mapper.readTree(response.getBody());
            if (root.path("status").asInt(-1) != 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "外部搜索服务未接受图片");
            }
            String url = root.path("data").path("url").asText("");
            validateHttpUrl(url);
            return URLDecoder.decode(url, StandardCharsets.UTF_8.name());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "外部搜索服务调用失败");
        }
    }

    private String findResultEndpoint(String pageUrl) {
        try {
            String body = get(pageUrl);
            Matcher matcher = FIRST_URL.matcher(body);
            if (!matcher.find()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "外部搜索结果格式已变化");
            }
            String resultUrl = matcher.group(1).replace("\\/", "/");
            validateHttpUrl(resultUrl);
            return resultUrl;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "外部搜索结果读取失败");
        }
    }

    private List<ExternalImageSearchResult> readResults(String resultUrl) {
        try {
            JsonNode root = mapper.readTree(get(resultUrl));
            JsonNode list = root.path("data").path("list");
            if (!list.isArray()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "外部搜索结果格式已变化");
            }
            List<ExternalImageSearchResult> results = new ArrayList<>();
            for (JsonNode item : list) {
                String thumb = safeUrl(item.path("thumbUrl").asText(""));
                String from = safeUrl(item.path("fromUrl").asText(""));
                if (thumb == null && from == null) continue;
                ExternalImageSearchResult result = new ExternalImageSearchResult();
                result.setThumbUrl(thumb);
                result.setFromUrl(from);
                result.setTitle(trimTitle(item.path("title").asText("")));
                results.add(result);
                if (results.size() == 30) break;
            }
            return results;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "外部搜索结果解析失败");
        }
    }

    private String get(String url) {
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "外部搜索服务返回失败");
        }
        return response.getBody();
    }

    private String safeUrl(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            validateHttpUrl(value);
            return value;
        } catch (BusinessException ignored) {
            return null;
        }
    }

    private String trimTitle(String value) {
        String title = value == null ? "" : value.trim();
        return title.length() > 200 ? title.substring(0, 200) : title;
    }

    private void validateHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 HTTP/HTTPS 图片地址");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址不合法");
        }
    }
}

