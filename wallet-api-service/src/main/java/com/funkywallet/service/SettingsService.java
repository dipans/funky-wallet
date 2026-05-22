package com.funkywallet.service;

import com.funkywallet.client.chain.ChainAdapterClient;
import com.funkywallet.model.entity.AppSetting;
import com.funkywallet.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String KEY_SOLANA_NONCE_FUNDER = "solana_nonce_funder_address";

    private final AppSettingRepository settingRepository;
    private final ChainAdapterClient chainClient;

    @Transactional(readOnly = true)
    public Map<String, Object> getSettings() {
        String funderAddress = get(KEY_SOLANA_NONCE_FUNDER);
        // Also fetch the address the chain adapter has configured via its keypair env var
        String adapterFunderAddress = chainClient.getSolanaNonceFunderAddress();
        return Map.of(
            "solanaNonceFunderAddress", funderAddress,
            "solanaNonceFunderConfigured", !adapterFunderAddress.isBlank(),
            "solanaNonceFunderKeypairAddress", adapterFunderAddress
        );
    }

    @Transactional
    public void setSolanaNonceFunder(String address) {
        upsert(KEY_SOLANA_NONCE_FUNDER, address == null ? "" : address.trim());
    }

    private String get(String key) {
        return settingRepository.findById(key)
            .map(AppSetting::getValue)
            .orElse("");
    }

    private void upsert(String key, String value) {
        AppSetting s = settingRepository.findById(key).orElseGet(() -> {
            AppSetting n = new AppSetting(); n.setKey(key); return n;
        });
        s.setValue(value);
        settingRepository.save(s);
    }
}
