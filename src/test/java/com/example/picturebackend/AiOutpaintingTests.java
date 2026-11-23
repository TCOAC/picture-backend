package com.example.picturebackend;

import com.example.picturebackend.model.entity.User;
import com.example.picturebackend.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StreamUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiOutpaintingTests {
    private static final AtomicReference<String> MODEL_REQUEST = new AtomicReference<>();
    private static final AtomicReference<String> AUTH_HEADER = new AtomicReference<>();
    private static final HttpServer SERVER;

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVER.createContext("/edits", exchange -> {
                MODEL_REQUEST.set(new String(StreamUtils.copyToByteArray(exchange.getRequestBody()),
                        StandardCharsets.ISO_8859_1));
                AUTH_HEADER.set(exchange.getRequestHeaders().getFirst("Authorization"));
                String response = "{\"data\":[{\"b64_json\":\"" +
                        Base64.getEncoder().encodeToString(png(8, 6)) + "\"}]}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            SERVER.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void modelProperties(DynamicPropertyRegistry registry) {
        registry.add("app.ai.image-api-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/edits");
        registry.add("app.ai.api-key", () -> "test-key");
        registry.add("app.ai.model", () -> "test-model");
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired ObjectMapper json;

    @Test
    void sendsTransparentMaskAndSavesReturnedImage() throws Exception {
        User owner = new User();
        owner.setUserAccount("paint_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        owner.setUserPassword(users.getEncryptPassword("test-pass-123"));
        owner.setUserName("owner");
        owner.setUserRole("user");
        assertTrue(users.save(owner));
        String login = "{\"userAccount\":\"" + owner.getUserAccount() +
                "\",\"userPassword\":\"test-pass-123\"}";
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getRequest().getSession(false);

        String upload = mvc.perform(multipart("/picture/upload")
                .file(new MockMultipartFile("file", "source.png", "image/png", png(4, 3)))
                .session(session)).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(upload).get("data").get("id").asText();
        String result = mvc.perform(post("/picture/ai/outpaint").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"scale\":1.5}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode image = json.readTree(result).get("data");
        assertEquals(8, image.get("picWidth").asInt());
        assertEquals(6, image.get("picHeight").asInt());
        assertTrue(MODEL_REQUEST.get().contains("name=\"mask\""));
        assertTrue(MODEL_REQUEST.get().contains("name=\"image\""));
        assertTrue(MODEL_REQUEST.get().contains("transparent border"));
        assertEquals("Bearer test-key", AUTH_HEADER.get());
    }

    @Test
    void createsAndPollsOutpaintingTask() throws Exception {
        User owner = new User();
        owner.setUserAccount("task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        owner.setUserPassword(users.getEncryptPassword("test-pass-123"));
        owner.setUserName("task-owner");
        owner.setUserRole("user");
        assertTrue(users.save(owner));
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userAccount\":\"" + owner.getUserAccount() + "\",\"userPassword\":\"test-pass-123\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getRequest().getSession(false);
        String upload = mvc.perform(multipart("/picture/upload").file(
                        new MockMultipartFile("file", "task.png", "image/png", png(4, 3))).session(session))
                .andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        String id = json.readTree(upload).get("data").get("id").asText();
        String created = mvc.perform(post("/picture/ai/outpaint/task").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + id + "\",\"scale\":1.5}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String taskId = json.readTree(created).get("data").get("taskId").asText();
        JsonNode state = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            String polled = mvc.perform(get("/picture/ai/outpaint/task")
                            .param("taskId", taskId).session(session))
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn().getResponse().getContentAsString();
            state = json.readTree(polled).get("data");
            if ("SUCCEEDED".equals(state.get("status").asText())) break;
            Thread.sleep(50L);
        }
        assertNotNull(state);
        assertEquals("SUCCEEDED", state.get("status").asText());
        assertEquals(8, state.get("result").get("picWidth").asInt());
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, Color.BLUE.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
