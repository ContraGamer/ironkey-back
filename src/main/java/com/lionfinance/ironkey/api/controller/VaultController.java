package com.lionfinance.ironkey.api.controller;

import com.lionfinance.ironkey.api.dto.vault.request.CreateVaultItemRequest;
import com.lionfinance.ironkey.api.dto.vault.request.UpdateVaultItemRequest;
import com.lionfinance.ironkey.api.dto.vault.response.PasswordHistoryResponse;
import com.lionfinance.ironkey.api.dto.vault.response.VaultItemResponse;
import com.lionfinance.ironkey.security.userdetails.IronKeyUserDetails;
import com.lionfinance.ironkey.service.VaultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vault")
@RequiredArgsConstructor
public class VaultController {

    private final VaultService vaultService;

    // -------------------------------------------------------------------------
    // Vault activo
    // -------------------------------------------------------------------------

    @GetMapping
    public List<VaultItemResponse> listItems(@AuthenticationPrincipal IronKeyUserDetails principal) {
        return vaultService.listItems(principal.getUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VaultItemResponse createItem(@Valid @RequestBody CreateVaultItemRequest request,
                                        @AuthenticationPrincipal IronKeyUserDetails principal) {
        return vaultService.createItem(principal.getUserId(), request);
    }

    @GetMapping("/{id}")
    public VaultItemResponse getItem(@PathVariable UUID id,
                                     @AuthenticationPrincipal IronKeyUserDetails principal) {
        return vaultService.getItem(principal.getUserId(), id);
    }

    @PutMapping("/{id}")
    public VaultItemResponse updateItem(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateVaultItemRequest request,
                                        @AuthenticationPrincipal IronKeyUserDetails principal) {
        return vaultService.updateItem(principal.getUserId(), id, request);
    }

    // Soft delete — mueve a papelera
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable UUID id,
                           @AuthenticationPrincipal IronKeyUserDetails principal) {
        vaultService.deleteItem(principal.getUserId(), id);
    }

    // -------------------------------------------------------------------------
    // Papelera
    // Spring MVC da prioridad a literales sobre path variables,
    // por eso /trash y /trash no colisionan con /{id}
    // -------------------------------------------------------------------------

    @GetMapping("/trash")
    public List<VaultItemResponse> listTrash(@AuthenticationPrincipal IronKeyUserDetails principal) {
        return vaultService.listTrash(principal.getUserId());
    }

    @DeleteMapping("/trash")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void emptyTrash(@AuthenticationPrincipal IronKeyUserDetails principal) {
        vaultService.emptyTrash(principal.getUserId());
    }

    @PostMapping("/{id}/restore")
    public VaultItemResponse restoreItem(@PathVariable UUID id,
                                         @AuthenticationPrincipal IronKeyUserDetails principal) {
        return vaultService.restoreItem(principal.getUserId(), id);
    }

    // Eliminación permanente — solo funciona si el ítem ya está en papelera
    @DeleteMapping("/{id}/purge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purgeItem(@PathVariable UUID id,
                          @AuthenticationPrincipal IronKeyUserDetails principal) {
        vaultService.purgeItem(principal.getUserId(), id);
    }

    @GetMapping("/{id}/history")
    public List<PasswordHistoryResponse> getItemHistory(@PathVariable UUID id,
                                                        @AuthenticationPrincipal IronKeyUserDetails principal) {
        return vaultService.getItemHistory(principal.getUserId(), id);
    }
}
