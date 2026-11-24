package com.example.learning.auth;

import com.example.learning.auth.security.Hashes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AuthTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanLoginAndReadRbacData() throws Exception {
        LoginResult login = login("admin", "Admin@12345", null);
        assertThat(login.code()).isZero();

        JsonNode roles = getJson("/api/admin/roles", login.token());
        assertThat(roles.path("code").asInt()).isZero();
        assertThat(roles.path("data").isArray()).isTrue();
        assertThat(roles.path("data").size()).isGreaterThanOrEqualTo(3);
        assertThat(login.permissions()).contains("rbac:role:write", "rbac:user:write");

        JsonNode users = getJson("/api/admin/users?keyword=admin", login.token());
        assertThat(users.path("code").asInt()).isZero();
        assertThat(users.path("data").path("total").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode tree = getJson("/api/admin/roles/tree", login.token());
        assertThat(tree.path("data").isArray()).isTrue();
        assertThat(tree.path("data").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void adminCanCreateRolesAndDsdConstraint() throws Exception {
        LoginResult admin = login("admin", "Admin@12345", null);

        long reviewerRole = createRole(admin.token(), "ROLE_TEST_REVIEWER", "测试审核员");
        long publisherRole = createRole(admin.token(), "ROLE_TEST_PUBLISHER", "测试发布员");

        Map<String, Object> constraint = new HashMap<>();
        constraint.put("code", "DSD_TEST_MAKER_CHECKER");
        constraint.put("name", "测试审核发布互斥");
        constraint.put("type", "DSD");
        constraint.put("maxRoles", 1);
        constraint.put("description", "测试动态职责分离");
        constraint.put("enabled", true);
        constraint.put("roleIds", List.of(reviewerRole, publisherRole));
        JsonNode createdConstraint = postJson("/api/admin/constraints", constraint, admin.token());
        assertThat(createdConstraint.path("code").asInt()).isZero();

        Map<String, Object> user = new HashMap<>();
        user.put("username", "dsduser");
        user.put("password", "PurpleLake@2026");
        user.put("displayName", "DSD 用户");
        user.put("email", "dsduser@example.com");
        user.put("phone", "");
        user.put("roleIds", List.of(reviewerRole, publisherRole));
        JsonNode createdUser = postJson("/api/admin/users", user, admin.token());
        assertThat(createdUser.path("code").asInt()).isZero();

        JsonNode conflict = loginRaw("dsduser", "PurpleLake@2026", null);
        assertThat(conflict.path("code").asInt()).isEqualTo(40901);
        assertThat(conflict.path("data").path("roleChoiceRequired").asBoolean()).isTrue();
        assertThat(conflict.path("data").path("options").size()).isGreaterThan(0);

        LoginResult single = login("dsduser", "PurpleLake@2026", List.of("ROLE_TEST_REVIEWER"));
        assertThat(single.activeRoles()).containsExactly("ROLE_TEST_REVIEWER");
    }

    @Test
    void ssdBlocksConflictingRoleAssignment() throws Exception {
        LoginResult sysadmin = login("sysadmin", "SysAdmin@12345", null);
        JsonNode users = getJson("/api/admin/users?keyword=sysadmin", sysadmin.token());
        long userId = users.path("data").path("items").get(0).path("id").asLong();

        JsonNode roles = getJson("/api/admin/roles", sysadmin.token());
        long sysAdminId = findRoleId(roles.path("data"), "ROLE_SYS_ADMIN");
        long auditorId = findRoleId(roles.path("data"), "ROLE_SECURITY_AUDITOR");

        Map<String, Object> body = Map.of("roleIds", List.of(sysAdminId, auditorId));
        MvcResult result = mockMvc.perform(put("/api/admin/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + sysadmin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = json(result);
        assertThat(response.path("code").asInt()).isEqualTo(40902);
        assertThat(response.path("message").asText()).contains("静态职责分离");
    }

    @Test
    void refreshTokenIsRotatedAndOldTokenRejected() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "account", "admin", "password", "Admin@12345"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refresh = login.getResponse().getCookie("refresh_token");
        assertThat(refresh).isNotNull();

        MvcResult rotated = mockMvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rotatedBody = json(rotated);
        assertThat(rotatedBody.path("code").asInt()).isZero();
        assertThat(rotatedBody.path("data").path("accessToken").asText()).isNotBlank();
        Cookie newRefresh = rotated.getResponse().getCookie("refresh_token");
        assertThat(newRefresh).isNotNull();
        assertThat(newRefresh.getValue()).isNotEqualTo(refresh.getValue());

        MvcResult replay = mockMvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(replay).path("code").asInt()).isEqualTo(40105);
    }

    @Test
    void registerWithEmailCodeAndResetPassword() throws Exception {
        String email = "newuser@example.com";
        JsonNode sent = postJson("/api/auth/otp/send", Map.of(
                "scene", "REGISTER", "channel", "EMAIL", "target", email), null);
        assertThat(sent.path("code").asInt()).isZero();
        String code = sent.path("data").path("debugCode").asText();
        assertThat(code).hasSize(6);

        Map<String, Object> register = new HashMap<>();
        register.put("username", "newuser");
        register.put("password", "Learner@2026Pass");
        register.put("confirmPassword", "Learner@2026Pass");
        register.put("displayName", "新同学");
        register.put("email", email);
        register.put("phone", "");
        register.put("emailCode", code);
        JsonNode registered = postJson("/api/auth/register", register, null);
        assertThat(registered.path("code").asInt()).isZero();
        assertThat(registered.path("data").path("user").path("username").asText()).isEqualTo("newuser");
        assertThat(registered.path("data").path("permissions").toString()).contains("resource:read");

        JsonNode forgot = postJson("/api/auth/forgot", Map.of("account", "sso_user"), null);
        assertThat(forgot.path("code").asInt()).isZero();
        String resetCode = forgot.path("data").path("debugCode").asText();
        assertThat(resetCode).hasSize(6);

        JsonNode reset = postJson("/api/auth/reset", Map.of(
                "account", "sso_user",
                "code", resetCode,
                "newPassword", "BlueLake@2026",
                "confirmPassword", "BlueLake@2026"), null);
        assertThat(reset.path("code").asInt()).isZero();

        LoginResult ssoUser = login("sso_user", "BlueLake@2026", null);
        assertThat(ssoUser.code()).isZero();
    }

    @Test
    void ssoAuthorizationCodeWithPkce() throws Exception {
        String verifier = "test-code-verifier-0123456789-abcdefghijklmnopqrstuvwxyz";
        String challenge = Hashes.pkceS256(verifier);
        String redirectUri = "http://localhost:5173/sso/callback";

        MvcResult authorize = mockMvc.perform(get("/api/auth/sso/authorize")
                        .param("client_id", "learning-web")
                        .param("redirect_uri", redirectUri)
                        .param("state", "state-123")
                        .param("response_type", "code")
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isFound())
                .andReturn();
        String location = authorize.getResponse().getHeader("Location");
        assertThat(location).contains("code=").contains("state=state-123");
        String code = UriComponentsBuilder.fromUriString(location).build()
                .getQueryParams().getFirst("code");

        JsonNode token = postJson("/api/auth/sso/token", Map.of(
                "code", code,
                "clientId", "learning-web",
                "redirectUri", redirectUri,
                "codeVerifier", verifier), null);
        assertThat(token.path("code").asInt()).isZero();
        assertThat(token.path("data").path("user").path("username").asText()).isEqualTo("sso_user");
    }

    @Test
    void wrongPasswordUsesUniformMessage() throws Exception {
        JsonNode response = loginRaw("student", "WrongPassword@1", null);
        assertThat(response.path("code").asInt()).isEqualTo(40101);
        assertThat(response.path("message").asText()).isEqualTo("账号或密码错误");

        JsonNode unknown = loginRaw("not-exist-user", "WrongPassword@1", null);
        assertThat(unknown.path("code").asInt()).isEqualTo(40101);
        assertThat(unknown.path("message").asText()).isEqualTo("账号或密码错误");
    }

    // ------------------------------------------------------------------

    private record LoginResult(String token, List<String> permissions, List<String> activeRoles, int code) {
    }

    private LoginResult login(String account, String password, List<String> roles) throws Exception {
        JsonNode node = loginRaw(account, password, roles);
        assertThat(node.path("code").asInt())
                .withFailMessage("login failed: %s", node)
                .isZero();
        return new LoginResult(
                node.path("data").path("accessToken").asText(),
                objectMapper.convertValue(node.path("data").path("permissions"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                objectMapper.convertValue(node.path("data").path("activeRoleCodes"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                node.path("code").asInt());
    }

    private long createRole(String token, String code, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("description", "integration test role");
        body.put("parentId", null);
        body.put("maxMembers", null);
        body.put("requiredRoleId", null);
        body.put("sortOrder", 500);
        JsonNode response = postJson("/api/admin/roles", body, token);
        assertThat(response.path("code").asInt())
                .withFailMessage("create role failed: %s", response)
                .isZero();
        return response.path("data").path("id").asLong();
    }

    private JsonNode loginRaw(String account, String password, List<String> roles) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("account", account);
        body.put("password", password);
        if (roles != null) {
            body.put("activeRoleCodes", roles);
        }
        return postJson("/api/auth/login/password", body, null);
    }

    private JsonNode postJson(String path, Object body, String token) throws Exception {
        var request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return json(result);
    }

    private JsonNode getJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private long findRoleId(JsonNode roles, String code) {
        for (JsonNode role : roles) {
            if (code.equals(role.path("code").asText())) {
                return role.path("id").asLong();
            }
        }
        throw new AssertionError("role not found: " + code);
    }
}
