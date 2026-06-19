package com.lionfinance.ironkey.api.controller;

import com.lionfinance.ironkey.api.dto.settings.request.UpdateUserSettingsRequest;
import com.lionfinance.ironkey.api.dto.settings.response.UserSettingsResponse;
import com.lionfinance.ironkey.security.userdetails.IronKeyUserDetails;
import com.lionfinance.ironkey.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public UserSettingsResponse getSettings(@AuthenticationPrincipal IronKeyUserDetails principal) {
        return settingsService.getSettings(principal.getUserId());
    }

    @PutMapping
    public UserSettingsResponse updateSettings(@Valid @RequestBody UpdateUserSettingsRequest request,
                                               @AuthenticationPrincipal IronKeyUserDetails principal) {
        return settingsService.updateSettings(principal.getUserId(), request);
    }
}
