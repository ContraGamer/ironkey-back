package com.lionfinance.ironkey.domain.repository;

import com.lionfinance.ironkey.domain.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    List<PasswordHistory> findTop10ByVaultItemIdOrderByCreatedAtDesc(UUID vaultItemId);

    // Keeps only the 10 most recent entries for a given vault item
    @Modifying
    @Query(value = """
        DELETE FROM password_history
        WHERE vault_item_id = :vaultItemId
          AND id NOT IN (
              SELECT id FROM password_history
              WHERE vault_item_id = :vaultItemId
              ORDER BY created_at DESC
              LIMIT 10
          )
        """, nativeQuery = true)
    void trimHistory(@Param("vaultItemId") UUID vaultItemId);
}
