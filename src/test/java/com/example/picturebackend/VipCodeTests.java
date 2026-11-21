package com.example.picturebackend;

import com.example.picturebackend.model.entity.User;
import com.example.picturebackend.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VipCodeTests {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired ObjectMapper json;

    private User createUser(String role) {
        User user = new User();
        user.setUserAccount("vip_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        user.setUserPassword(users.getEncryptPassword("test-pass-123"));
        user.setUserRole(role);
        user.setUserName("tester");
        assertTrue(users.save(user));
        return user;
    }

    private MockHttpSession login(User user) throws Exception {
        String body = "{\"userAccount\":\"" + user.getUserAccount() +
                "\",\"userPassword\":\"test-pass-123\"}";
        return (MockHttpSession) mvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getRequest().getSession(false);
    }

    private String issue(MockHttpSession admin, int days) throws Exception {
        String response = mvc.perform(post("/user/vip/issue").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationDays\":" + days + "}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("data").asText();
    }

    private JsonNode redeem(MockHttpSession session, String code) throws Exception {
        String response = mvc.perform(post("/user/exchange/vip").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vipCode\":\"" + code + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    @Test
    void onlyAdminCanIssueAndCodeCanBeRedeemedOnce() throws Exception {
        User admin = createUser("admin");
        User member = createUser("user");
        User other = createUser("user");
        MockHttpSession memberSession = login(member);
        mvc.perform(post("/user/vip/issue").session(memberSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationDays\":30}"))
                .andExpect(jsonPath("$.code").value(40101));

        String code = issue(login(admin), 30);
        assertTrue(code.matches("VIP-[0-9A-F]{32}"));
        assertEquals(0, redeem(memberSession, code).get("code").asInt());
        User updated = users.getById(member.getId());
        assertNotNull(updated.getVipExpireTime());
        assertEquals("user", updated.getUserRole());
        assertEquals(40000, redeem(login(other), code).get("code").asInt());
        assertNull(users.getById(other.getId()).getVipExpireTime());
    }

    @Test
    void secondCodeExtendsExistingMembershipAndPreservesAdminRole() throws Exception {
        User admin = createUser("admin");
        MockHttpSession session = login(admin);
        assertEquals(0, redeem(session, issue(session, 7)).get("code").asInt());
        Date firstExpiry = users.getById(admin.getId()).getVipExpireTime();
        JsonNode second = redeem(session, issue(session, 30));
        assertEquals(0, second.get("code").asInt());
        assertEquals("admin", second.get("data").get("userRole").asText());
        Date secondExpiry = users.getById(admin.getId()).getVipExpireTime();
        assertEquals(30, Duration.between(firstExpiry.toInstant(), secondExpiry.toInstant()).toDays());
        assertNotNull(second.get("data").get("vipExpireTime"));
    }
}
