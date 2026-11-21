package com.example.picturebackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PictureBackendApplicationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    void contextLoads() {
    }

    @Test
    void userUploadWaitsForReviewAndIsOnlyVisibleToTheOwner() throws Exception {
        String registration = mvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userAccount\":\"gallery_user\",\"userPassword\":\"gallery-pass-123\",\"checkPassword\":\"gallery-pass-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String userId = json.readTree(registration).get("data").asText();
        MockHttpSession session=(MockHttpSession)mvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userAccount\":\"gallery_user\",\"userPassword\":\"gallery-pass-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getRequest().getSession(false);
        byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJ2kAAAAASUVORK5CYII=");
        mvc.perform(multipart("/picture/upload").file(new MockMultipartFile("file","pixel.png","image/png",png)).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.reviewStatus").value(0));
        mvc.perform(post("/picture/list/page/vo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"current\":1,\"pageSize\":20,\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(post("/picture/my/list/page/vo").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"current\":1,\"pageSize\":20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

}
