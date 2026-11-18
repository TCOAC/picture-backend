package com.example.picturebackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestGuard extends OncePerRequestFilter {
    @Value("${app.cors.allowed-origins}")private String origins;
    private final Map<String,long[]> limits=new ConcurrentHashMap<>();
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        if(!"GET".equals(req.getMethod()) && !"OPTIONS".equals(req.getMethod())){
            String origin=req.getHeader("Origin");
            String own=req.getScheme()+"://"+req.getServerName()+((req.getServerPort()==80||req.getServerPort()==443)?"":":"+req.getServerPort());
            if(origin!=null && !origin.equals(own) && Arrays.stream(origins.split(",")).map(String::trim).noneMatch(origin::equals)){
                res.sendError(403,"不允许的请求来源");return;
            }
            String path=req.getRequestURI();boolean auth=path.endsWith("/login")||path.endsWith("/register");
            if(auth || path.contains("/upload") || path.contains("/import/") || path.contains("/ai/")){
                long minute=System.currentTimeMillis()/60000;
                if(limits.size()>10000)limits.entrySet().removeIf(e->e.getValue()[0]<minute);
                long[] bucket=limits.computeIfAbsent(req.getRemoteAddr()+(auth?":auth":":upload"),k->new long[]{minute,0});
                synchronized(bucket){if(bucket[0]!=minute){bucket[0]=minute;bucket[1]=0;}
                    if(++bucket[1]>(auth?30:60)){res.sendError(429,"请求过于频繁");return;}}
            }
        }chain.doFilter(req,res);
    }
}
