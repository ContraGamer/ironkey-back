package com.lionfinance.ironkey.service;

import com.lionfinance.ironkey.domain.repository.RefreshTokenRepository;
import com.lionfinance.ironkey.domain.repository.VaultItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final VaultItemRepository    vaultItemRepository;

    @Value("${ironkey.cleanup.trash-retention-days:30}")
    private int trashRetentionDays;

    @Transactional
    @Scheduled(cron = "${ironkey.cleanup.cron-tokens:0 0 2 * * *}")
    public void purgeExpiredTokens() {
        int removed = refreshTokenRepository.deleteExpiredAndRevoked(OffsetDateTime.now());
        log.info("Cleanup[tokens]: {} expired/revoked refresh tokens removed", removed);
    }

    @Transactional
    @Scheduled(cron = "${ironkey.cleanup.cron-trash:0 0 3 * * *}")
    public void purgeOldTrash() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(trashRetentionDays);
        int removed = vaultItemRepository.purgeDeletedBefore(cutoff);
        log.info("Cleanup[trash]: {} vault items older than {} days permanently removed",
                removed, trashRetentionDays);
    }
}
