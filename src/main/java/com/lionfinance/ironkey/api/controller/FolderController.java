package com.lionfinance.ironkey.api.controller;

import com.lionfinance.ironkey.api.dto.vault.request.CreateFolderRequest;
import com.lionfinance.ironkey.api.dto.vault.request.UpdateFolderRequest;
import com.lionfinance.ironkey.api.dto.vault.response.FolderResponse;
import com.lionfinance.ironkey.security.userdetails.IronKeyUserDetails;
import com.lionfinance.ironkey.service.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @GetMapping
    public List<FolderResponse> listFolders(@AuthenticationPrincipal IronKeyUserDetails principal) {
        return folderService.listFolders(principal.getUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderResponse createFolder(@Valid @RequestBody CreateFolderRequest request,
                                       @AuthenticationPrincipal IronKeyUserDetails principal) {
        return folderService.createFolder(principal.getUserId(), request);
    }

    @PutMapping("/{id}")
    public FolderResponse updateFolder(@PathVariable UUID id,
                                       @Valid @RequestBody UpdateFolderRequest request,
                                       @AuthenticationPrincipal IronKeyUserDetails principal) {
        return folderService.updateFolder(principal.getUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@PathVariable UUID id,
                             @AuthenticationPrincipal IronKeyUserDetails principal) {
        folderService.deleteFolder(principal.getUserId(), id);
    }
}
