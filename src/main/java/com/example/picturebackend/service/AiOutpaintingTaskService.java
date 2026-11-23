package com.example.picturebackend.service;

import com.example.picturebackend.common.BaseResponse;
import com.example.picturebackend.controller.AiImageController;
import com.example.picturebackend.exception.BusinessException;
import com.example.picturebackend.exception.ErrorCode;
import com.example.picturebackend.model.entity.Picture;
import com.example.picturebackend.model.entity.User;
import com.example.picturebackend.model.vo.AiTaskVO;
import com.example.picturebackend.model.vo.PictureVO;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import javax.servlet.http.HttpServletRequest;

@Service
public class AiOutpaintingTaskService {
    private final AiImageController aiImageController;
    private final PictureService pictures;
    private final Map<String, AiTaskVO> tasks = new ConcurrentHashMap<>();

    public AiOutpaintingTaskService(AiImageController aiImageController, PictureService pictures) {
        this.aiImageController = aiImageController;
        this.pictures = pictures;
    }

    public AiTaskVO create(Long pictureId, double scale, String prompt, User user, HttpServletRequest request) {
        if (pictureId == null || user == null || !Double.isFinite(scale) || scale < 1.1 || scale > 2.0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "扩图参数不合法");
        }
        Picture picture = pictures.getById(pictureId);
        if (picture == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        String taskId = UUID.randomUUID().toString().replace("-", "");
        AiTaskVO task = new AiTaskVO();
        task.setTaskId(taskId);
        task.setPictureId(pictureId);
        task.setStatus("PENDING");
        task.setCreatedAt(new Date());
        task.setUpdatedAt(task.getCreatedAt());
        tasks.put(taskId, task);
        CompletableFuture.runAsync(() -> run(taskId, pictureId, scale, prompt, request));
        return copy(task);
    }

    public AiTaskVO get(String taskId, User user) {
        AiTaskVO task = tasks.get(taskId);
        if (task == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        Picture picture = pictures.getById(task.getPictureId());
        if (picture == null || !picture.getUserId().equals(user.getId())
                && !"admin".equals(user.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无任务访问权限");
        }
        return copy(task);
    }

    public void run(String taskId, Long pictureId, double scale, String prompt, HttpServletRequest request) {
        AiTaskVO task = tasks.get(taskId);
        if (task == null) return;
        task.setStatus("RUNNING");
        task.setUpdatedAt(new Date());
        try {
            AiImageController.OutpaintRequest body = new AiImageController.OutpaintRequest();
            body.setId(pictureId);
            body.setScale(scale);
            body.setPrompt(prompt);
            BaseResponse<PictureVO> response = aiImageController.outpaint(body, request);
            if (response.getCode() != 0) {
                throw new BusinessException(response.getCode(), response.getMessage());
            }
            task.setStatus("SUCCEEDED");
            task.setResult(response.getData());
        } catch (BusinessException e) {
            task.setStatus("FAILED");
            task.setMessage(e.getMessage());
            task.setUpdatedAt(new Date());
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setMessage("扩图任务失败");
            task.setUpdatedAt(new Date());
        }
    }

    private AiTaskVO copy(AiTaskVO source) {
        AiTaskVO copy = new AiTaskVO();
        copy.setTaskId(source.getTaskId());
        copy.setPictureId(source.getPictureId());
        copy.setStatus(source.getStatus());
        copy.setMessage(source.getMessage());
        copy.setResult(source.getResult());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }
}
