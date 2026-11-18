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
    @Value("${app.ai.image-api-url:}")private String endpoint;
    @Value("${app.ai.api-key:}")private String apiKey;
    @Value("${app.ai.model:}")private String model;
    @Data public static class EditRequest {private Long id;private String prompt;}
    @PostMapping("/edit")
    public BaseResponse<PictureVO> edit(@RequestBody EditRequest body,HttpServletRequest request){
        User user=users.getLoginUser(request);ThrowUtils.throwIf(body==null||body.getId()==null||!StringUtils.hasText(body.getPrompt())||body.getPrompt().length()>500,ErrorCode.PARAMS_ERROR,"请输入 1-500 字的编辑描述");
        ThrowUtils.throwIf(!StringUtils.hasText(endpoint)||!StringUtils.hasText(apiKey)||!StringUtils.hasText(model),ErrorCode.OPERATION_ERROR,"AI 图片服务尚未配置；请设置 AI_IMAGE_API_URL、AI_IMAGE_API_KEY 和 AI_IMAGE_MODEL");
        Picture p=pictures.getById(body.getId());access.edit(p,user);
        try{
            String key=p.getUrl().substring(p.getUrl().lastIndexOf('/')+1);byte[] bytes=files.read(key);
            HttpHeaders imageHeaders=new HttpHeaders();imageHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            imageHeaders.setContentDispositionFormData("image",p.getName()+"."+p.getPicFormat());
            MultiValueMap<String,Object> form=new LinkedMultiValueMap<>();form.add("model",model);form.add("prompt",body.getPrompt());
            form.add("image",new HttpEntity<>(new ByteArrayResource(bytes){public String getFilename(){return p.getName()+"."+p.getPicFormat();}},imageHeaders));
            HttpHeaders headers=new HttpHeaders();headers.setBearerAuth(apiKey);headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            org.springframework.http.client.SimpleClientHttpRequestFactory factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);factory.setReadTimeout(120000);
            Map response=new RestTemplate(factory).postForObject(URI.create(endpoint),new HttpEntity<>(form,headers),Map.class);
            List data=(List)response.get("data");ThrowUtils.throwIf(data==null||data.isEmpty(),ErrorCode.OPERATION_ERROR,"AI 服务未返回图片");
            Map item=(Map)data.get(0);byte[] output;
            if(item.get("b64_json")!=null)output=Base64.getDecoder().decode((String)item.get("b64_json"));
            else if(item.get("url")!=null)output=fetcher.fetch((String)item.get("url"),2*1024*1024);
            else throw new BusinessException(ErrorCode.OPERATION_ERROR,"AI 服务返回格式不受支持");
            String format=Optional.ofNullable((String)item.get("output_format")).orElse("png");
            return pictureController.uploadPicture(new ByteArrayMultipartFile(output,p.getName()+"."+format),p.getId(),p.getSpaceId(),p.getName(),p.getIntroduction(),p.getCategory(),p.getTags(),request);
        }catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException(ErrorCode.OPERATION_ERROR,"AI 图片编辑失败，请检查模型服务配置");}
    }
}
