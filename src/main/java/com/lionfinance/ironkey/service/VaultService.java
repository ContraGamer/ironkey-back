package com.lionfinance.ironkey.service;

import com.lionfinance.ironkey.api.dto.vault.request.CreateVaultItemRequest;
import com.lionfinance.ironkey.api.dto.vault.request.UpdateVaultItemRequest;
import com.lionfinance.ironkey.api.dto.vault.response.VaultItemResponse;
import com.lionfinance.ironkey.domain.entity.Folder;
import com.lionfinance.ironkey.domain.entity.VaultItem;
import com.lionfinance.ironkey.domain.repository.FolderRepository;
import com.lionfinance.ironkey.domain.repository.UserRepository;
import com.lionfinance.ironkey.domain.repository.VaultItemRepository;
import com.lionfinance.ironkey.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class VaultService {

    private final VaultItemRepository vaultItemRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Vault activo
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<VaultItemResponse> listItems(UUID userId) {
        return vaultItemRepository.findAllByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VaultItemResponse getItem(UUID userId, UUID itemId) {
        return vaultItemRepository.findByIdAndUserId(itemId, userId)
                .filter(item -> !item.isDeleted())
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Ítem"));
    }

    public VaultItemResponse createItem(UUID userId, CreateVaultItemRequest request) {
        var user = userRepository.getReferenceById(userId);
        var folder = resolveFolder(request.folderId(), userId);

        var item = VaultItem.builder()
                .user(user)
                .folder(folder)
                .encryptedData(request.encryptedData())
                .iv(request.iv())
                .build();

        return toResponse(vaultItemRepository.save(item));
    }

    public VaultItemResponse updateItem(UUID userId, UUID itemId, UpdateVaultItemRequest request) {
        var item = vaultItemRepository.findByIdAndUserId(itemId, userId)
                .filter(i -> !i.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Ítem"));

        item.setEncryptedData(request.encryptedData());
        item.setIv(request.iv());
        item.setFolder(resolveFolder(request.folderId(), userId));

        return toResponse(vaultItemRepository.save(item));
    }

    // Soft delete — mueve a papelera
    public void deleteItem(UUID userId, UUID itemId) {
        var item = vaultItemRepository.findByIdAndUserId(itemId, userId)
                .filter(i -> !i.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Ítem"));

        item.setDeletedAt(OffsetDateTime.now());
        vaultItemRepository.save(item);
    }

    // -------------------------------------------------------------------------
    // Papelera
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<VaultItemResponse> listTrash(UUID userId) {
        return vaultItemRepository.findAllByUserIdAndDeletedAtIsNotNull(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public VaultItemResponse restoreItem(UUID userId, UUID itemId) {
        var item = vaultItemRepository.findByIdAndUserId(itemId, userId)
                .filter(VaultItem::isDeleted)
                .orElseThrow(() -> new ResourceNotFoundException("Ítem en papelera"));

        item.setDeletedAt(null);
        return toResponse(vaultItemRepository.save(item));
    }

    // Elimina permanentemente un ítem de la papelera
    public void purgeItem(UUID userId, UUID itemId) {
        var item = vaultItemRepository.findByIdAndUserId(itemId, userId)
                .filter(VaultItem::isDeleted)
                .orElseThrow(() -> new ResourceNotFoundException("Ítem en papelera"));

        vaultItemRepository.delete(item);
    }

    // Vacía la papelera completa del usuario
    public void emptyTrash(UUID userId) {
        vaultItemRepository.emptyTrashByUserId(userId);
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    private Folder resolveFolder(UUID folderId, UUID userId) {
        if (folderId == null) return null;
        return folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carpeta"));
    }

    private VaultItemResponse toResponse(VaultItem item) {
        return new VaultItemResponse(
                item.getId(),
                item.getEncryptedData(),
                item.getIv(),
                item.getFolder() != null ? item.getFolder().getId() : null,
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getDeletedAt()
        );
    }
}
