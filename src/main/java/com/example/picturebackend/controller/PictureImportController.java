package com.example.picturebackend.controller;

import com.example.picturebackend.common.*;
import com.example.picturebackend.exception.*;
import com.example.picturebackend.manager.*;
import com.example.picturebackend.service.UserService;
import lombok.*;
import org.jsoup.Jsoup;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/picture/import")
@RequiredArgsConstructor
public class PictureImportController {
    private final RemoteImageFetcher fetcher;private final PictureController pictures;private final UserService users;
    @Data public static class ImportRequest {private String url;private List<String> urls;private Long spaceId;private String name;}
    @PostMapping("/url")
    public BaseResponse<?> single(@RequestBody ImportRequest body,HttpServletRequest request){
        users.getLoginUser(request);
        return pictures.uploadPicture(new ByteArrayMultipartFile(fetcher.fetch(body.getUrl(),2*1024*1024),"import.png"),null,body.getSpaceId(),body.getName(),null,null,null,request);
    }
    @PostMapping("/batch")
    public BaseResponse<List<Map<String,Object>>> batch(@RequestBody ImportRequest body,HttpServletRequest request){
        ThrowUtils.throwIf(!users.isAdmin(users.getLoginUser(request)),ErrorCode.NO_AUTH_ERROR,"批量采集仅限管理员");
        List<String> urls=body.getUrls();
        if(urls==null && body.getUrl()!=null){
            urls=Jsoup.parse(new String(fetcher.fetch(body.getUrl(),1024*1024),java.nio.charset.StandardCharsets.UTF_8),body.getUrl())
                .select("img[src]").stream().map(el->el.absUrl("src")).filter(s->!s.isEmpty()).distinct().limit(10).collect(java.util.stream.Collectors.toList());
        }
        ThrowUtils.throwIf(urls==null || urls.isEmpty() || urls.size()>10,ErrorCode.PARAMS_ERROR,"每批最多 10 个链接");
        List<Map<String,Object>> result=new ArrayList<>();
        for(String url:urls){Map<String,Object> item=new LinkedHashMap<>();item.put("url",url);
            try{item.put("picture",pictures.uploadPicture(new ByteArrayMultipartFile(fetcher.fetch(url,2*1024*1024),"import.png"),null,body.getSpaceId(),body.getName(),null,null,null,request).getData());item.put("success",true);}
            catch(BusinessException e){item.put("success",false);item.put("message",e.getMessage());}result.add(item);
        }return ResultUtils.success(result);
    }
}
