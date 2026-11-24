package com.example.picturebackend.controller;

import com.example.picturebackend.common.*;
import com.example.picturebackend.domain.space.PictureAccess;
import com.example.picturebackend.exception.*;
import com.example.picturebackend.manager.*;
import com.example.picturebackend.model.entity.*;
import com.example.picturebackend.model.vo.PictureVO;
import com.example.picturebackend.service.*;
import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/picture/ai")
@RequiredArgsConstructor
public class AiImageController {
    private final PictureService pictures;private final UserService users;private final PictureAccess access;
    private final FileManager files;private final RemoteImageFetcher fetcher;private final PictureController pictureController;
    @javax.annotation.Resource
    @Lazy
    private com.example.picturebackend.service.AiOutpaintingTaskService tasks;
    @Value("${app.ai.image-api-url:}")private String endpoint;
    @Value("${app.ai.api-key:}")private String apiKey;
    @Value("${app.ai.model:}")private String model;
    @Data public static class EditRequest {private Long id;private String prompt;}
    @Data public static class OutpaintRequest {private Long id;private String prompt;private Double scale;}
    @PostMapping("/edit")
    public BaseResponse<PictureVO> edit(@RequestBody EditRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getId() == null || !StringUtils.hasText(body.getPrompt())
                || body.getPrompt().length() > 500, ErrorCode.PARAMS_ERROR, "请输入 1-500 字的编辑描述");
        User user = users.getLoginUser(request);
        Picture picture = editablePicture(body.getId(), user);
        return generate(picture, body.getPrompt().trim(), null, user);
    }

    @PostMapping("/outpaint")
    public BaseResponse<PictureVO> outpaint(@RequestBody OutpaintRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getId() == null || body.getScale() == null
                || !Double.isFinite(body.getScale()) || body.getScale() < 1.1 || body.getScale() > 2.0
                || body.getPrompt() != null && body.getPrompt().length() > 500,
                ErrorCode.PARAMS_ERROR, "扩图参数不合法");
        User user = users.getLoginUser(request);
        Picture picture = editablePicture(body.getId(), user);
        String prompt = "Extend this image naturally into the transparent border. Preserve the original center.";
        if (StringUtils.hasText(body.getPrompt())) {
            prompt += " " + body.getPrompt().trim();
        }
        try {
            OutpaintingCanvas canvas = OutpaintingCanvas.from(files.read(storageKey(picture)), body.getScale());
            return generate(picture, prompt, canvas, user);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "原图读取失败");
        }
    }

    @PostMapping("/outpaint/task")
    public BaseResponse<com.example.picturebackend.model.vo.AiTaskVO> createTask(
            @RequestBody OutpaintRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getId() == null || body.getScale() == null,
                ErrorCode.PARAMS_ERROR, "扩图参数不完整");
        User user = users.getLoginUser(request);
        access.edit(pictures.getById(body.getId()), user);
        return ResultUtils.success(tasks.create(body.getId(), body.getScale(), body.getPrompt(), user));
    }

    @GetMapping("/outpaint/task")
    public BaseResponse<com.example.picturebackend.model.vo.AiTaskVO> getTask(
            @RequestParam String taskId, HttpServletRequest request) {
        ThrowUtils.throwIf(!StringUtils.hasText(taskId), ErrorCode.PARAMS_ERROR, "任务编号不能为空");
        return ResultUtils.success(tasks.get(taskId, users.getLoginUser(request)));
    }

    public BaseResponse<PictureVO> outpaintForTask(OutpaintRequest body, User user) {
        ThrowUtils.throwIf(body == null || body.getId() == null || body.getScale() == null
                || !Double.isFinite(body.getScale()) || body.getScale() < 1.1 || body.getScale() > 2.0
                || body.getPrompt() != null && body.getPrompt().length() > 500,
                ErrorCode.PARAMS_ERROR, "扩图参数不合法");
        Picture picture = editablePicture(body.getId(), user);
        String prompt = "Extend this image naturally into the transparent border. Preserve the original center.";
        if (StringUtils.hasText(body.getPrompt())) prompt += " " + body.getPrompt().trim();
        try {
            OutpaintingCanvas canvas = OutpaintingCanvas.from(files.read(storageKey(picture)), body.getScale());
            return generate(picture, prompt, canvas, user);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "原图读取失败");
        }
    }

    private Picture editablePicture(Long id, User user) {
        Picture picture = pictures.getById(id);
        access.edit(picture, user);
        return picture;
    }

    private String storageKey(Picture picture) {
        return picture.getUrl().substring(picture.getUrl().lastIndexOf('/') + 1);
    }

    private BaseResponse<PictureVO> generate(Picture picture, String prompt, OutpaintingCanvas canvas,
                                             User user) {
        ThrowUtils.throwIf(!StringUtils.hasText(endpoint) || !StringUtils.hasText(apiKey)
                || !StringUtils.hasText(model), ErrorCode.OPERATION_ERROR,
                "AI 图片服务尚未配置；请设置 AI_IMAGE_API_URL、AI_IMAGE_API_KEY 和 AI_IMAGE_MODEL");
        try {
            byte[] image = canvas == null ? files.read(storageKey(picture)) : canvas.getImage();
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("model", model);
            form.add("prompt", prompt);
            String inputName = picture.getName() + (canvas == null ? "." + picture.getPicFormat() : ".png");
            form.add("image", imagePart(image, "image", inputName,
                    canvas == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.IMAGE_PNG));
            if (canvas != null) {
                form.add("mask", imagePart(canvas.getMask(), "mask", "mask.png", MediaType.IMAGE_PNG));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(120000);
            Map response = new RestTemplate(factory).postForObject(URI.create(endpoint),
                    new HttpEntity<>(form, headers), Map.class);
            ThrowUtils.throwIf(response == null || !(response.get("data") instanceof List),
                    ErrorCode.OPERATION_ERROR, "AI 服务未返回图片");
            List data = (List) response.get("data");
            ThrowUtils.throwIf(data.isEmpty() || !(data.get(0) instanceof Map),
                    ErrorCode.OPERATION_ERROR, "AI 服务未返回图片");
            Map item = (Map) data.get(0);
            byte[] output;
            if (item.get("b64_json") instanceof String) {
                output = Base64.getDecoder().decode((String) item.get("b64_json"));
            } else if (item.get("url") instanceof String) {
                output = fetcher.fetch((String) item.get("url"), 2 * 1024 * 1024);
            } else {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 服务返回格式不受支持");
            }
            String format = item.get("output_format") instanceof String
                    ? (String) item.get("output_format") : "png";
            return pictureController.uploadPictureForUser(new ByteArrayMultipartFile(output, picture.getName() + "." + format),
                    picture.getId(), picture.getSpaceId(), picture.getName(), picture.getIntroduction(),
                    picture.getCategory(), picture.getTags(), user);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 图片生成失败，请检查模型服务配置");
        }
    }

    private HttpEntity<ByteArrayResource> imagePart(byte[] bytes, String field, String filename,
                                                    MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentDispositionFormData(field, filename);
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new HttpEntity<>(resource, headers);
    }
}

