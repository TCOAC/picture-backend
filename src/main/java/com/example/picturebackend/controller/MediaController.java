package com.example.picturebackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.picturebackend.domain.space.PictureAccess;
import com.example.picturebackend.exception.*;
import com.example.picturebackend.manager.FileManager;
import com.example.picturebackend.model.entity.Picture;
import com.example.picturebackend.service.PictureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class MediaController {
    private final FileManager files; private final PictureService pictures; private final PictureAccess access;
    @GetMapping("/media/{key:.+}")
    public ResponseEntity<byte[]> media(@PathVariable String key,@RequestParam(defaultValue="false") boolean thumbnail,
            @RequestParam(defaultValue="false") boolean download,@RequestParam(required=false) String code,HttpServletRequest request)throws IOException{
        ThrowUtils.throwIf(!key.matches("[a-f0-9]{64}\\.(jpg|png|webp)"),ErrorCode.NOT_FOUND_ERROR);
        // Multiple records may reference the same content; any authorized reference grants access to those bytes.
        List<Picture> refs=pictures.list(new QueryWrapper<Picture>().eq("fileHash",key.substring(0,64)).last("LIMIT 100"));
        Picture allowed=null;
        for(Picture p:refs){try{access.read(p,request,code);allowed=p;break;}catch(BusinessException ignored){}}
        ThrowUtils.throwIf(allowed==null,ErrorCode.NO_AUTH_ERROR,"无图片访问权限");
        if(download)pictures.update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Picture>()
                .eq("id",allowed.getId()).setSql("downloadCount = downloadCount + 1"));
        byte[] content=files.read(key+(thumbnail?".thumb.jpg":""));
        String type=thumbnail || key.endsWith("jpg")?"image/jpeg":key.endsWith("png")?"image/png":"image/webp";
        return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff")
                .header("Content-Disposition",download?"attachment; filename=\"picture."+allowed.getPicFormat()+"\"":"inline")
                .contentType(MediaType.parseMediaType(type)).body(content);
    }
}
