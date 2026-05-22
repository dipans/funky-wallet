package com.funkywallet.controller;

import com.funkywallet.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public Map<String, Object> getSettings() {
        return settingsService.getSettings();
    }

    @PutMapping("/solana-nonce-funder")
    public Map<String, Object> setSolanaNonceFunder(@RequestBody Map<String, String> body) {
        settingsService.setSolanaNonceFunder(body.get("address"));
        return settingsService.getSettings();
    }
}
