package com.lionfinance.ironkey.service;

import com.lionfinance.ironkey.api.dto.settings.request.UpdateUserSettingsRequest;
import com.lionfinance.ironkey.api.dto.settings.response.UserSettingsResponse;
import com.lionfinance.ironkey.domain.repository.UserRepository;
import com.lionfinance.ironkey.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class SettingsService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario"));

        return new UserSettingsResponse(
                user.getRequireReprompt(),
                user.getVaultTimeoutMinutes()
        );
    }

    public UserSettingsResponse updateSettings(UUID userId, UpdateUserSettingsRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario"));

        user.setRequireReprompt(request.requireReprompt());
        user.setVaultTimeoutMinutes(request.vaultTimeoutMinutes());
        userRepository.save(user);

        return new UserSettingsResponse(
                user.getRequireReprompt(),
                user.getVaultTimeoutMinutes()
        );
    }
}
