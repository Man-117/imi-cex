package org.william.cex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.william.cex.dto.request.AdminRegisterRequest;
import org.william.cex.dto.request.LoginRequest;
import org.william.cex.dto.request.RegisterUserRequest;
import org.william.cex.support.IntegrationTestBase;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PublicEndpointAccessTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${admin.registration.key}")
    private String adminRegistrationKey;

    @Test
    @DisplayName("Open authentication endpoints are accessible without credentials")
    void testOpenAuthEndpointsWithoutCredential() throws Exception {
        String userEmail = "public-auth-" + UUID.randomUUID() + "@example.com";
        RegisterUserRequest registerUserRequest = RegisterUserRequest.builder()
                .email(userEmail)
                .password("StrongPassword123!")
                .build();

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerUserRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists());

        LoginRequest loginUserRequest = LoginRequest.builder()
                .email(userEmail)
                .password("StrongPassword123!")
                .build();

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginUserRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());

        String adminEmail = "public-admin-" + UUID.randomUUID() + "@example.com";
        AdminRegisterRequest registerAdminRequest = AdminRegisterRequest.builder()
                .email(adminEmail)
                .password("StrongAdminPassword123!")
                .adminKey(adminRegistrationKey)
                .build();

        mockMvc.perform(post("/v1/admin/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerAdminRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists());

        LoginRequest loginAdminRequest = LoginRequest.builder()
                .email(adminEmail)
                .password("StrongAdminPassword123!")
                .build();

        mockMvc.perform(post("/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginAdminRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    @DisplayName("Open docs and health endpoints are accessible without credentials")
    void testOpenDocsAndHealthEndpointsWithoutCredential() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UP")));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"openapi\"")));

        MvcResult swaggerUiEntry = mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectLocation = swaggerUiEntry.getResponse().getHeader("Location");
        if (redirectLocation != null && redirectLocation.startsWith("/")) {
            mockMvc.perform(get(redirectLocation))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Protected endpoints still reject unauthenticated access")
    void testProtectedEndpointStillRequiresCredential() throws Exception {
        mockMvc.perform(get("/v1/balance/USD"))
                .andExpect(status().isUnauthorized());
    }
}
