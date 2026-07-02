package com.lionfinance.ironkey.service;

import com.lionfinance.ironkey.api.dto.vault.request.CreateVaultItemRequest;
import com.lionfinance.ironkey.api.dto.vault.request.UpdateVaultItemRequest;
import com.lionfinance.ironkey.domain.entity.Folder;
import com.lionfinance.ironkey.domain.entity.User;
import com.lionfinance.ironkey.domain.entity.VaultItem;
import com.lionfinance.ironkey.domain.repository.FolderRepository;
import com.lionfinance.ironkey.domain.repository.PasswordHistoryRepository;
import com.lionfinance.ironkey.domain.repository.UserRepository;
import com.lionfinance.ironkey.domain.repository.VaultItemRepository;
import com.lionfinance.ironkey.domain.entity.PasswordHistory;
import com.lionfinance.ironkey.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VaultServiceTest {

    @Mock VaultItemRepository vaultItemRepository;
    @Mock FolderRepository folderRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordHistoryRepository historyRepository;

    @InjectMocks VaultService vaultService;

    private UUID userId;
    private UUID itemId;
    private User testUser;
    private VaultItem activeItem;
    private VaultItem deletedItem;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        itemId = UUID.randomUUID();

        testUser = User.builder()
                .id(userId)
                .email("user@ironkey.dev")
                .masterPasswordHash("hash")
                .kdfSalt("salt")
                .protectedSymmetricKey("key")
                .protectedSymmetricKeyIv("iv")
                .build();

        activeItem = VaultItem.builder()
                .id(itemId)
                .user(testUser)
                .encryptedData("encryptedBlob")
                .iv("itemIv")
                .build();

        deletedItem = VaultItem.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .encryptedData("deletedBlob")
                .iv("deletedIv")
                .deletedAt(OffsetDateTime.now().minusHours(1))
                .build();
    }

    // -------------------------------------------------------------------------
    // listItems
    // -------------------------------------------------------------------------

    @Test
    void listItems_returnsOnlyActiveItems() {
        when(vaultItemRepository.findAllByUserIdAndDeletedAtIsNull(userId))
                .thenReturn(List.of(activeItem));

        var result = vaultService.listItems(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).encryptedData()).isEqualTo("encryptedBlob");
    }

    // -------------------------------------------------------------------------
    // getItem
    // -------------------------------------------------------------------------

    @Test
    void getItem_activeItem_returnsResponse() {
        when(vaultItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(activeItem));

        var result = vaultService.getItem(userId, itemId);

        assertThat(result.id()).isEqualTo(itemId);
        assertThat(result.encryptedData()).isEqualTo("encryptedBlob");
    }

    @Test
    void getItem_deletedItem_throwsResourceNotFoundException() {
        when(vaultItemRepository.findByIdAndUserId(deletedItem.getId(), userId)).thenReturn(Optional.of(deletedItem));

        assertThatThrownBy(() -> vaultService.getItem(userId, deletedItem.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getItem_notFound_throwsResourceNotFoundException() {
        when(vaultItemRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vaultService.getItem(userId, itemId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // createItem
    // -------------------------------------------------------------------------

    @Test
    void createItem_withoutFolder_savesItem() {
        when(userRepository.getReferenceById(userId)).thenReturn(testUser);
        when(vaultItemRepository.save(any(VaultItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateVaultItemRequest("newBlob", "newIv", null);
        var result = vaultService.createItem(userId, request);

        assertThat(result.encryptedData()).isEqualTo("newBlob");
        assertThat(result.folderId()).isNull();
        verify(vaultItemRepository).save(any(VaultItem.class));
    }

    @Test
    void createItem_withValidFolder_assignsFolder() {
        UUID folderId = UUID.randomUUID();
        Folder folder = Folder.builder().id(folderId).user(testUser).encryptedName("name").iv("iv").build();

        when(userRepository.getReferenceById(userId)).thenReturn(testUser);
        when(folderRepository.findByIdAndUserId(folderId, userId)).thenReturn(Optional.of(folder));
        when(vaultItemRepository.save(any(VaultItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateVaultItemRequest("blob", "iv", folderId);
        var result = vaultService.createItem(userId, request);

        assertThat(result.folderId()).isEqualTo(folderId);
    }

    @Test
    void createItem_folderNotFound_throwsResourceNotFoundException() {
        UUID folderId = UUID.randomUUID();
        when(userRepository.getReferenceById(userId)).thenReturn(testUser);
        when(folderRepository.findByIdAndUserId(folderId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vaultService.createItem(userId, new CreateVaultItemRequest("b", "i", folderId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // updateItem
    // -------------------------------------------------------------------------

    @Test
    void updateItem_activeItem_updatesAndReturns() {
        when(vaultItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(activeItem));
        when(vaultItemRepository.save(any(VaultItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(PasswordHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateVaultItemRequest("updatedBlob", "newIv", null);
        var result = vaultService.updateItem(userId, itemId, request);

        assertThat(result.encryptedData()).isEqualTo("updatedBlob");
        assertThat(result.iv()).isEqualTo("newIv");
    }

    // -------------------------------------------------------------------------
    // deleteItem (soft delete)
    // -------------------------------------------------------------------------

    @Test
    void deleteItem_activeItem_setsDeletedAt() {
        when(vaultItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(activeItem));
        when(vaultItemRepository.save(any(VaultItem.class))).thenAnswer(inv -> inv.getArgument(0));

        vaultService.deleteItem(userId, itemId);

        assertThat(activeItem.getDeletedAt()).isNotNull();
        assertThat(activeItem.isDeleted()).isTrue();
    }

    @Test
    void deleteItem_alreadyDeleted_throwsResourceNotFoundException() {
        when(vaultItemRepository.findByIdAndUserId(deletedItem.getId(), userId))
                .thenReturn(Optional.of(deletedItem));

        assertThatThrownBy(() -> vaultService.deleteItem(userId, deletedItem.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Papelera
    // -------------------------------------------------------------------------

    @Test
    void listTrash_returnsOnlyDeletedItems() {
        when(vaultItemRepository.findAllByUserIdAndDeletedAtIsNotNull(userId))
                .thenReturn(List.of(deletedItem));

        var result = vaultService.listTrash(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deletedAt()).isNotNull();
    }

    @Test
    void restoreItem_deletedItem_clearsDeletedAt() {
        when(vaultItemRepository.findByIdAndUserId(deletedItem.getId(), userId))
                .thenReturn(Optional.of(deletedItem));
        when(vaultItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = vaultService.restoreItem(userId, deletedItem.getId());

        assertThat(result.deletedAt()).isNull();
        assertThat(deletedItem.isDeleted()).isFalse();
    }

    @Test
    void restoreItem_activeItem_throwsResourceNotFoundException() {
        when(vaultItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(activeItem));

        assertThatThrownBy(() -> vaultService.restoreItem(userId, itemId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void purgeItem_deletedItem_deletesFromDb() {
        when(vaultItemRepository.findByIdAndUserId(deletedItem.getId(), userId))
                .thenReturn(Optional.of(deletedItem));

        vaultService.purgeItem(userId, deletedItem.getId());

        verify(vaultItemRepository).delete(deletedItem);
    }

    @Test
    void emptyTrash_callsRepository() {
        vaultService.emptyTrash(userId);

        verify(vaultItemRepository).emptyTrashByUserId(userId);
    }
}
