package com.example.picturebackend.config;

import com.example.picturebackend.domain.space.*;
import com.example.picturebackend.model.entity.*;
import com.example.picturebackend.service.*;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.*;
import java.util.concurrent.*;
import javax.servlet.http.HttpSession;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class CollaborationConfig implements WebSocketConfigurer {
    private final PictureService pictures;private final UserService users;private final PictureAccess access;private final CollaborationLocks locks;private final ObjectMapper json;
    @Value("${app.cors.allowed-origins}")private String origins;
    private final Map<Long,Set<WebSocketSession>> rooms=new ConcurrentHashMap<>();
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){
        registry.addHandler(new Handler(),"/ws/picture").setAllowedOrigins(origins.split(",")).addInterceptors(new HandshakeInterceptor(){
            public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attrs){
                try{
                    ServletServerHttpRequest servlet=(ServletServerHttpRequest)request;
                    User user=users.getLoginUser(servlet.getServletRequest());
                    Long id=Long.valueOf(UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("pictureId"));
                    Picture p=pictures.getById(id);access.edit(p,user);
                    if(p.getSpaceId()==null)return false;
                    attrs.put("pictureId",id);attrs.put("userId",user.getId());attrs.put("httpSession",servlet.getServletRequest().getSession());return true;
                }catch(Exception e){return false;}
            }
            public void afterHandshake(ServerHttpRequest r,ServerHttpResponse s,WebSocketHandler h,Exception e){}
        });
    }
    private class Handler extends TextWebSocketHandler {
        public void afterConnectionEstablished(WebSocketSession session)throws Exception{
            session.setTextMessageSizeLimit(4096);Long id=(Long)session.getAttributes().get("pictureId");
            Set<WebSocketSession> room=rooms.computeIfAbsent(id,k->ConcurrentHashMap.newKeySet());
            if(room.size()>=20){session.close(CloseStatus.POLICY_VIOLATION);return;}room.add(session);
        }
        private User authorize(WebSocketSession session){
            HttpSession http=(HttpSession)session.getAttributes().get("httpSession");
            Object state=http.getAttribute(com.example.picturebackend.constant.UserConstant.USER_LOGIN_STATE);
            if(state==null)throw new IllegalStateException();
            User user=users.getById((Long)session.getAttributes().get("userId"));
            access.edit(pictures.getById((Long)session.getAttributes().get("pictureId")),user);return user;
        }
        protected void handleTextMessage(WebSocketSession session,TextMessage message)throws Exception{
            try{
                User user=authorize(session);Long id=(Long)session.getAttributes().get("pictureId");
                JsonNode input=json.readTree(message.getPayload());String type=input.path("type").asText();
                if("release".equals(type)){locks.release(id,user.getId());return;}
                if(!Arrays.asList("lock","heartbeat","transform","refresh").contains(type))return;
                boolean granted=locks.acquire(id,user.getId());
                Map<String,Object> event=new LinkedHashMap<>();event.put("type",granted?type:"busy");event.put("userId",user.getId());
                if(granted && "transform".equals(type)){
                    int rotation=input.path("rotation").asInt();if(rotation%90!=0 || Math.abs(rotation)>360)return;
                    event.put("rotation",rotation);event.put("flip",input.path("flip").asBoolean());
                }
                TextMessage output=new TextMessage(json.writeValueAsString(event));
                for(WebSocketSession peer:rooms.getOrDefault(id,Collections.emptySet())){
                    try{authorize(peer);synchronized(peer){if(peer.isOpen())peer.sendMessage(output);}}catch(Exception e){peer.close(CloseStatus.POLICY_VIOLATION);}
                }
            }catch(Exception e){session.close(CloseStatus.POLICY_VIOLATION);}
        }
        public void afterConnectionClosed(WebSocketSession session,CloseStatus status){
            Long id=(Long)session.getAttributes().get("pictureId");Set<WebSocketSession> room=rooms.get(id);
            if(room!=null){room.remove(session);if(room.isEmpty())rooms.remove(id,room);}
            locks.release(id,(Long)session.getAttributes().get("userId"));
        }
    }
}
