package com.lionfinance.ironkey.domain.repository;

import com.lionfinance.ironkey.domain.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    List<Folder> findAllByUserId(UUID userId);

    // Verifica pertenencia al usuario en la misma query — nunca exponer carpetas ajenas
    Optional<Folder> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);
}
