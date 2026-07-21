package com.lionfinance.ironkey.domain.repository;

import com.lionfinance.ironkey.domain.entity.VaultItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaultItemRepository extends JpaRepository<VaultItem, UUID> {

    // Vault activo del usuario
    List<VaultItem> findAllByUserIdAndDeletedAtIsNull(UUID userId);

    // Papelera del usuario
    List<VaultItem> findAllByUserIdAndDeletedAtIsNotNull(UUID userId);

    // Verifica pertenencia al usuario en la misma query
    Optional<VaultItem> findByIdAndUserId(UUID id, UUID userId);

    // Vaciar papelera — elimina definitivamente todos los ítems borrados del usuario
    @Modifying
    @Query("DELETE FROM VaultItem v WHERE v.user.id = :userId AND v.deletedAt IS NOT NULL")
    void emptyTrashByUserId(@Param("userId") UUID userId);

    // Purgar ítems en papelera más antiguos de X días (para un job de limpieza automática)
    @Modifying
    @Query("DELETE FROM VaultItem v WHERE v.deletedAt IS NOT NULL AND v.deletedAt < :before")
    int purgeDeletedBefore(@Param("before") OffsetDateTime before);
}
