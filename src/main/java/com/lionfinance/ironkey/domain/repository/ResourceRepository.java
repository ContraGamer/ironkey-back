package com.lionfinance.ironkey.domain.repository;

import com.lionfinance.ironkey.domain.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    Optional<Resource> findByName(String name);

    List<Resource> findAllByRequiresAuth(Boolean requiresAuth);
}
