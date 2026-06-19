package com.lionfinance.ironkey.service;

import com.lionfinance.ironkey.api.dto.vault.request.CreateFolderRequest;
import com.lionfinance.ironkey.api.dto.vault.request.UpdateFolderRequest;
import com.lionfinance.ironkey.api.dto.vault.response.FolderResponse;
import com.lionfinance.ironkey.domain.entity.Folder;
import com.lionfinance.ironkey.domain.repository.FolderRepository;
import com.lionfinance.ironkey.domain.repository.UserRepository;
import com.lionfinance.ironkey.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<FolderResponse> listFolders(UUID userId) {
        return folderRepository.findAllByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public FolderResponse createFolder(UUID userId, CreateFolderRequest request) {
        var user = userRepository.getReferenceById(userId);

        var folder = Folder.builder()
                .user(user)
                .encryptedName(request.encryptedName())
                .iv(request.iv())
                .build();

        return toResponse(folderRepository.save(folder));
    }

    public FolderResponse updateFolder(UUID userId, UUID folderId, UpdateFolderRequest request) {
        var folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carpeta"));

        folder.setEncryptedName(request.encryptedName());
        folder.setIv(request.iv());

        return toResponse(folderRepository.save(folder));
    }

    public void deleteFolder(UUID userId, UUID folderId) {
        var folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carpeta"));

        // ON DELETE SET NULL en la migración garantiza que los ítems de esta carpeta
        // quedan en el vault raíz en lugar de ser eliminados
        folderRepository.delete(folder);
    }

    private FolderResponse toResponse(Folder folder) {
        return new FolderResponse(
                folder.getId(),
                folder.getEncryptedName(),
                folder.getIv(),
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }
}
