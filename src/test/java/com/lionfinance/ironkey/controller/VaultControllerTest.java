package com.lionfinance.ironkey.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lionfinance.ironkey.api.controller.VaultController;
import com.lionfinance.ironkey.api.dto.vault.request.CreateVaultItemRequest;
import com.lionfinance.ironkey.api.dto.vault.request.UpdateVaultItemRequest;
import com.lionfinance.ironkey.api.dto.vault.response.VaultItemResponse;
import com.lionfinance.ironkey.api.handler.GlobalExceptionHandler;
import com.lionfinance.ironkey.domain.entity.User;
import com.lionfinance.ironkey.exception.ResourceNotFoundException;
import com.lionfinance.ironkey.security.userdetails.IronKeyUserDetails;
import com.lionfinance.ironkey.service.VaultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = VaultController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(GlobalExceptionHandler.class)
class VaultControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean VaultService vaultService;

    private UUID userId;
    private UUID itemId;
    private IronKeyUserDetails mockPrincipal;
    private VaultItemResponse mockItemResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        itemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        User user = User.builder()
                .id(userId)
                .email("user@ironkey.dev")
                .masterPasswordHash("hash")
                .kdfSalt("salt")
                .protectedSymmetricKey("key")
                .protectedSymmetricKeyIv("iv")
                .build();

        mockPrincipal = new IronKeyUserDetails(user);

        mockItemResponse = new VaultItemResponse(
                itemId, "encryptedBlob", "itemIv", null,
                OffsetDateTime.now(), OffsetDateTime.now(), null
        );
    }

    // -------------------------------------------------------------------------
    // GET /vault
    // -------------------------------------------------------------------------

    @Test
    void listItems_withAuth_returns200AndList() throws Exception {
        when(vaultService.listItems(userId)).thenReturn(List.of(mockItemResponse));

        mvc.perform(get("/api/v1/vault").with(mockAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].encryptedData").value("encryptedBlob"))
                .andExpect(jsonPath("$[0].iv").value("itemIv"));
    }

    @Test
    void listItems_emptyVault_returnsEmptyArray() throws Exception {
        when(vaultService.listItems(userId)).thenReturn(List.of());

        mvc.perform(get("/api/v1/vault").with(mockAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // -------------------------------------------------------------------------
    // POST /vault
    // -------------------------------------------------------------------------

    @Test
    void createItem_validRequest_returns201() throws Exception {
        when(vaultService.createItem(eq(userId), any())).thenReturn(mockItemResponse);

        mvc.perform(post("/api/v1/vault")
                        .with(mockAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CreateVaultItemRequest("blob", "iv", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.encryptedData").value("encryptedBlob"));
    }

    @Test
    void createItem_missingEncryptedData_returns400() throws Exception {
        mvc.perform(post("/api/v1/vault")
                        .with(mockAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CreateVaultItemRequest("", "iv", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.encryptedData").exists());
    }

    // -------------------------------------------------------------------------
    // GET /vault/{id}
    // -------------------------------------------------------------------------

    @Test
    void getItem_existingItem_returns200() throws Exception {
        when(vaultService.getItem(userId, itemId)).thenReturn(mockItemResponse);

        mvc.perform(get("/api/v1/vault/{id}", itemId).with(mockAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId.toString()));
    }

    @Test
    void getItem_notFound_returns404() throws Exception {
        when(vaultService.getItem(userId, itemId)).thenThrow(new ResourceNotFoundException("Ítem"));

        mvc.perform(get("/api/v1/vault/{id}", itemId).with(mockAuth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ítem no encontrado"));
    }

    // -------------------------------------------------------------------------
    // PUT /vault/{id}
    // -------------------------------------------------------------------------

    @Test
    void updateItem_validRequest_returns200() throws Exception {
        var updated = new VaultItemResponse(itemId, "updatedBlob", "newIv", null,
                OffsetDateTime.now(), OffsetDateTime.now(), null);
        when(vaultService.updateItem(eq(userId), eq(itemId), any())).thenReturn(updated);

        mvc.perform(put("/api/v1/vault/{id}", itemId)
                        .with(mockAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new UpdateVaultItemRequest("updatedBlob", "newIv", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encryptedData").value("updatedBlob"));
    }

    // -------------------------------------------------------------------------
    // DELETE /vault/{id}  (soft delete)
    // -------------------------------------------------------------------------

    @Test
    void deleteItem_existingItem_returns204() throws Exception {
        doNothing().when(vaultService).deleteItem(userId, itemId);

        mvc.perform(delete("/api/v1/vault/{id}", itemId).with(mockAuth()))
                .andExpect(status().isNoContent());
    }

    // -------------------------------------------------------------------------
    // Papelera
    // -------------------------------------------------------------------------

    @Test
    void listTrash_returns200AndDeletedItems() throws Exception {
        var trashedItem = new VaultItemResponse(
                UUID.randomUUID(), "deletedBlob", "iv", null,
                OffsetDateTime.now().minusDays(2), OffsetDateTime.now().minusDays(2),
                OffsetDateTime.now().minusHours(1)
        );
        when(vaultService.listTrash(userId)).thenReturn(List.of(trashedItem));

        mvc.perform(get("/api/v1/vault/trash").with(mockAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deletedAt").exists());
    }

    @Test
    void emptyTrash_returns204() throws Exception {
        doNothing().when(vaultService).emptyTrash(userId);

        mvc.perform(delete("/api/v1/vault/trash").with(mockAuth()))
                .andExpect(status().isNoContent());
    }

    @Test
    void restoreItem_returns200WithRestoredItem() throws Exception {
        when(vaultService.restoreItem(userId, itemId)).thenReturn(mockItemResponse);

        mvc.perform(post("/api/v1/vault/{id}/restore", itemId).with(mockAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedAt").doesNotExist());
    }

    @Test
    void purgeItem_returns204() throws Exception {
        doNothing().when(vaultService).purgeItem(userId, itemId);

        mvc.perform(delete("/api/v1/vault/{id}/purge", itemId).with(mockAuth()))
                .andExpect(status().isNoContent());
    }

    // -------------------------------------------------------------------------
    // Helper — inyecta el principal en el SecurityContext sin pasar por el filtro JWT
    // -------------------------------------------------------------------------

    private org.springframework.test.web.servlet.request.RequestPostProcessor mockAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                mockPrincipal, null, mockPrincipal.getAuthorities()
        ));
    }
}
