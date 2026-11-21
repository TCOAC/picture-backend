package com.example.picturebackend;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.picturebackend.constant.UserConstant;
import com.example.picturebackend.domain.space.SpaceDomainService;
import com.example.picturebackend.mapper.SpaceMapper;
import com.example.picturebackend.model.entity.*;
import com.example.picturebackend.service.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GalleryAuthorizationTests {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired PictureService pictures;
    @Autowired SpaceDomainService spaces;
    @Autowired SpaceMapper spaceMapper;
    @Autowired ObjectMapper json;

    private User user(String role){
        User u=new User();u.setUserAccount("test_"+UUID.randomUUID().toString().replace("-", "").substring(0,15));
        u.setUserPassword(users.getEncryptPassword("test-pass-123"));u.setUserRole(role);u.setUserName("tester");assertTrue(users.save(u));assertNotNull(users.getById(u.getId()));return u;
    }
    private MockHttpSession session(User user)throws Exception{
        String body="{\"userAccount\":\""+user.getUserAccount()+"\",\"userPassword\":\"test-pass-123\"}";
        return (MockHttpSession)mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getRequest().getSession(false);
    }
    private MockMultipartFile png(int rgb)throws Exception{
        BufferedImage image=new BufferedImage(4,3,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<3;y++)for(int x=0;x<4;x++)image.setRGB(x,y,rgb);
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);
        return new MockMultipartFile("file","test.png","image/png",out.toByteArray());
    }
    private JsonNode upload(MockHttpSession session,Long space)throws Exception{
        org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder req=multipart("/picture/upload").file(png(0x38a8db));
        if(space!=null)req.param("spaceId",space.toString());
        String response=mvc.perform(req.session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("data");
    }
    @Test void teamViewerCanReadButCannotEditAndRevocationTakesEffect()throws Exception{
        User owner=user("user"),viewer=user("user"),stranger=user("user");
        Space team=spaces.create("Team",1,owner);spaces.setMember(team.getId(),viewer.getId(),"viewer",owner);
        JsonNode p=upload(session(owner),team.getId());String id=p.get("id").asText(),url=p.get("url").asText().substring(4);
        mvc.perform(get(url).session(session(viewer))).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get(url).session(session(stranger))).andExpect(jsonPath("$.code").value(40101));
        mvc.perform(post("/picture/edit").session(session(viewer)).contentType(MediaType.APPLICATION_JSON).content("{\"id\":\""+id+"\",\"name\":\"forbidden\"}"))
            .andExpect(jsonPath("$.code").value(40101));
        spaces.removeMember(team.getId(),viewer.getId(),owner);
        mvc.perform(get(url).session(session(viewer))).andExpect(jsonPath("$.code").value(40101));
    }
    @Test void shareTokenCanBeRevokedAndIsNeverListed()throws Exception{
        User owner=user("user");Space space=spaces.create("Private",0,owner);MockHttpSession s=session(owner);
        JsonNode p=upload(s,space.getId());String id=p.get("id").asText(),url=p.get("url").asText().substring(4);
        String response=mvc.perform(post("/picture/share").session(s).contentType(MediaType.APPLICATION_JSON).content("{\"id\":\""+id+"\"}"))
            .andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        String code=json.readTree(response).get("data").asText();assertEquals(64,code.length());
        mvc.perform(get(url).param("code",code)).andExpect(content().contentType("image/png"));
        mvc.perform(post("/picture/space/list/page/vo").session(s).contentType(MediaType.APPLICATION_JSON).content("{\"spaceId\":\""+space.getId()+"\"}"))
            .andExpect(jsonPath("$.data.records[0].shareCode").isEmpty());
        mvc.perform(post("/picture/share/revoke").session(s).contentType(MediaType.APPLICATION_JSON).content("{\"id\":\""+id+"\"}"))
            .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get(url).param("code",code)).andExpect(jsonPath("$.code").value(40101));
    }
    @Test void quotaFailureLeavesNoPictureOrUsageAndDeletionReleasesUsage()throws Exception{
        User owner=user("user");Space space=spaces.create("Limited",0,owner);space.setMaxCount(1L);spaceMapper.updateById(space);
        MockHttpSession s=session(owner);JsonNode p=upload(s,space.getId());
        mvc.perform(multipart("/picture/upload").file(png(0xffffff)).param("spaceId",space.getId().toString()).session(s))
            .andExpect(jsonPath("$.code").value(50001));
        assertEquals(1L,spaces.require(space.getId()).getTotalCount());
        assertEquals(1,pictures.count(new QueryWrapper<Picture>().eq("spaceId",space.getId())));
        mvc.perform(post("/picture/delete").session(s).contentType(MediaType.APPLICATION_JSON).content("{\"id\":\""+p.get("id").asText()+"\"}"))
            .andExpect(jsonPath("$.code").value(0));
        assertEquals(0L,spaces.require(space.getId()).getTotalCount());assertEquals(0L,spaces.require(space.getId()).getTotalSize());
    }
    @Test void publicModerationAndEditingResetReviewMetadata()throws Exception{
        User owner=user("user"),admin=user("admin");MockHttpSession s=session(owner);JsonNode p=upload(s,null);String id=p.get("id").asText();
        String ownerFilter = "{\"userId\":\"" + owner.getId() + "\"}";
        mvc.perform(post("/picture/list/page/vo").contentType(MediaType.APPLICATION_JSON).content(ownerFilter))
            .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(post("/picture/review").session(session(admin)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\""+id+"\",\"reviewStatus\":1,\"reviewMessage\":\"approved\"}"))
            .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/picture/list/page/vo").contentType(MediaType.APPLICATION_JSON).content(ownerFilter))
            .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(post("/picture/edit").session(s).contentType(MediaType.APPLICATION_JSON).content("{\"id\":\""+id+"\",\"name\":\"changed\"}"))
            .andExpect(jsonPath("$.code").value(0));
        Picture stored=pictures.getById(id);assertEquals(0,stored.getReviewStatus());assertNull(stored.getReviewerId());assertNull(stored.getReviewTime());assertNull(stored.getReviewMessage());
        mvc.perform(post("/picture/list/page/vo").contentType(MediaType.APPLICATION_JSON).content(ownerFilter))
            .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void tagCategoryOnlyIncludesApprovedPublicPictures() throws Exception {
        User admin = user("admin");
        User owner = user("user");
        MockHttpSession adminSession = session(admin);
        MockHttpSession ownerSession = session(owner);
        Space privateSpace = spaces.create("Private tags", 0, owner);

        mvc.perform(multipart("/picture/upload").file(png(0x336699))
                .param("category", "Visible category")
                .param("tags", "[\"Visible tag\"]")
                .session(adminSession))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(multipart("/picture/upload").file(png(0x669933))
                .param("category", "Pending category")
                .param("tags", "[\"Pending tag\"]")
                .session(ownerSession))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(multipart("/picture/upload").file(png(0x993366))
                .param("spaceId", privateSpace.getId().toString())
                .param("category", "Private category")
                .param("tags", "[\"Private tag\"]")
                .session(ownerSession))
                .andExpect(jsonPath("$.code").value(0));

        String response = mvc.perform(get("/picture/tag_category"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = json.readTree(response).get("data");
        assertTrue(data.get("categoryList").toString().contains("Visible category"));
        assertTrue(data.get("tagList").toString().contains("Visible tag"));
        assertFalse(data.toString().contains("Pending"));
        assertFalse(data.toString().contains("Private"));
    }
}
