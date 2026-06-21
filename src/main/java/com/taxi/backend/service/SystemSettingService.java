package com.taxi.backend.service;

import com.taxi.backend.model.SystemSetting;
import com.taxi.backend.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Tizim sozlamalari — key-value o'qish/yozish.
 *
 * SURGE: talab narxi (surge) toggle'i shu yerda saqlanadi. Default = OFF —
 * agar qator bo'lmasa ham false qaytadi. Faqat admin yoqishi mumkin.
 */
@Service
public class SystemSettingService {

    public static final String SURGE_ENABLED = "surge_enabled";

    private final SystemSettingRepository repository;

    public SystemSettingService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return repository.findById(key)
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(defaultValue);
    }

    @Transactional
    public void setBoolean(String key, boolean value) {
        SystemSetting setting = repository.findById(key).orElseGet(() -> new SystemSetting(key, "false"));
        setting.setValue(Boolean.toString(value));
        setting.setUpdatedAt(LocalDateTime.now());
        repository.save(setting);
    }

    /** Surge yoqilganmi — default OFF (false). */
    public boolean isSurgeEnabled() {
        return getBoolean(SURGE_ENABLED, false);
    }

    @Transactional
    public void setSurgeEnabled(boolean enabled) {
        setBoolean(SURGE_ENABLED, enabled);
    }
}
