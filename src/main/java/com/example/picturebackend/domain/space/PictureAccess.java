package com.example.picturebackend.domain.space;

import com.example.picturebackend.exception.*;
import com.example.picturebackend.model.entity.*;
import com.example.picturebackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class PictureAccess {
    private final SpaceDomainService spaces;
    private final UserService users;
    public void edit(Picture p,User u){
        ThrowUtils.throwIf(p==null,ErrorCode.NOT_FOUND_ERROR);
        if(p.getSpaceId()!=null){spaces.check(p.getSpaceId(),u,"edit");return;}
        ThrowUtils.throwIf(!users.isAdmin(u) && !p.getUserId().equals(u.getId()),ErrorCode.NO_AUTH_ERROR);
    }
    public boolean shared(Picture p,String code){
        return code!=null && code.equals(p.getShareCode()) && p.getShareExpireTime()!=null
            && p.getShareExpireTime().after(new Date()) && (p.getSpaceId()!=null || Integer.valueOf(1).equals(p.getReviewStatus()));
    }
    public void read(Picture p,HttpServletRequest request,String code){
        ThrowUtils.throwIf(p==null,ErrorCode.NOT_FOUND_ERROR);
        if(shared(p,code))return;
        if(p.getSpaceId()!=null){spaces.check(p.getSpaceId(),users.getLoginUser(request),"read");return;}
        if(Integer.valueOf(1).equals(p.getReviewStatus()))return;
        edit(p,users.getLoginUser(request));
    }
}
