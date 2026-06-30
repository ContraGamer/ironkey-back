package com.lionfinance.ironkey.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lionfinance.ironkey.api.controller.AuthController;
import com.lionfinance.ironkey.api.dto.auth.request.LoginRequest;
import com.lionfinance.ironkey.api.dto.auth.request.RefreshRequest;
import com.lionfinance.ironkey.api.dto.auth.request.RegisterRequest;
import com.lionfinance.ironkey.api.dto.auth.response.AuthResponse;
import com.lionfinance.ironkey.api.dto.auth.response.KdfParamsResponse;
import com.lionfinance.ironkey.api.handler.GlobalExceptionHandler;
import com.lionfinance.ironkey.exception.EmailAlreadyExistsException;
import com.lionfinance.ironkey.exception.InvalidCredentialsException;
import com.lionfinance.ironkey.exception.TotpRequiredException;
import com.lionfinance.ironkey.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = AuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AuthService authService;

    private static final AuthResponse MOCK_AUTH = new AuthResponse(
            "access.token", "refresh.token", "encKey", "encIv", false, 15
    );

    // -------------------------------------------------------------------------
    // POST /register
    // -------------------------------------------------------------------------

    @Test
    void register_validRequest_returns201WithTokens() throws Exception {
        when(authService.register(any(), any(), any())).thenReturn(MOCK_AUTH);

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(buildRegisterRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access.token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh.token"))
                .andExpect(jsonPath("$.protectedSymmetricKey").value("encKey"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(any(), any(), any())).thenThrow(new EmailAlreadyExistsException());

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(buildRegisterRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void register_invalidEmail_returns400WithFieldErrors() throws Exception {
        var request = new RegisterRequest(
                "not-an-email", "hash", "salt", "argon2id", 3, 65536, 4, "key", "iv"
        );

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void register_missingRequiredFields_returns400() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    // -------------------------------------------------------------------------
    // GET /kdf-params
    // -------------------------------------------------------------------------

    @Test
    void getKdfParams_existingEmail_returns200() throws Exception {
        var kdfParams = new KdfParamsResponse("argon2id", 3, 65536, 4, "randomSalt");
        when(authService.getKdfParams("user@ironkey.dev")).thenReturn(kdfParams);

        mvc.perform(get("/api/v1/auth/kdf-params").param("email", "user@ironkey.dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kdfType").value("argon2id"))
                .andExpect(jsonPath("$.kdfSalt").value("randomSalt"));
    }

    @Test
    void getKdfParams_unknownEmail_returns401() throws Exception {
        when(authService.getKdfParams(any())).thenThrow(new InvalidCredentialsException());

        mvc.perform(get("/api/v1/auth/kdf-params").param("email", "ghost@ironkey.dev"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    // -------------------------------------------------------------------------
    // POST /login
    // -------------------------------------------------------------------------

    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        when(authService.login(any(), any(), any())).thenReturn(MOCK_AUTH);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest("user@ironkey.dev", "hash", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.token"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        when(authService.login(any(), any(), any())).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest("user@ironkey.dev", "wrong", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_totpRequired_returns401WithMessage() throws Exception {
        when(authService.login(any(), any(), any())).thenThrow(new TotpRequiredException());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest("user@ironkey.dev", "hash", null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Se requiere código 2FA"));
    }

    @Test
    void login_malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // POST /refresh
    // -------------------------------------------------------------------------

    @Test
    void refresh_validToken_returns200WithNewTokens() throws Exception {
        when(authService.refresh(any(), any(), any())).thenReturn(MOCK_AUTH);

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new RefreshRequest("rawToken"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RegisterRequest buildRegisterRequest() {
        return new RegisterRequest(
                "user@ironkey.dev", "clientHash", "kdfSalt",
                "argon2id", 3, 65536, 4, "encKey", "encIv"
        );
    }
}
