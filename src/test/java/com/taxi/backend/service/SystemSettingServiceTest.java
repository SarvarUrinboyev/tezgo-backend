package com.taxi.backend.service;

import com.taxi.backend.model.SystemSetting;
import com.taxi.backend.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceTest {

    @Mock private SystemSettingRepository repository;
    @InjectMocks private SystemSettingService service;

    @Test
    void isSurgeEnabled_noRow_defaultsFalse() {
        when(repository.findById(SystemSettingService.SURGE_ENABLED)).thenReturn(Optional.empty());
        assertFalse(service.isSurgeEnabled(), "Default OFF");
    }

    @Test
    void isSurgeEnabled_rowTrue_returnsTrue() {
        when(repository.findById(SystemSettingService.SURGE_ENABLED))
                .thenReturn(Optional.of(new SystemSetting(SystemSettingService.SURGE_ENABLED, "true")));
        assertTrue(service.isSurgeEnabled());
    }

    @Test
    void isSurgeEnabled_rowFalse_returnsFalse() {
        when(repository.findById(SystemSettingService.SURGE_ENABLED))
                .thenReturn(Optional.of(new SystemSetting(SystemSettingService.SURGE_ENABLED, "false")));
        assertFalse(service.isSurgeEnabled());
    }

    @Test
    void setSurgeEnabled_true_persistsTrue() {
        when(repository.findById(SystemSettingService.SURGE_ENABLED)).thenReturn(Optional.empty());
        service.setSurgeEnabled(true);

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(repository).save(captor.capture());
        assertEquals(SystemSettingService.SURGE_ENABLED, captor.getValue().getKey());
        assertEquals("true", captor.getValue().getValue());
    }

    @Test
    void setSurgeEnabled_false_updatesExistingRow() {
        SystemSetting existing = new SystemSetting(SystemSettingService.SURGE_ENABLED, "true");
        when(repository.findById(SystemSettingService.SURGE_ENABLED)).thenReturn(Optional.of(existing));

        service.setSurgeEnabled(false);

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(repository).save(captor.capture());
        assertEquals("false", captor.getValue().getValue());
    }

    @Test
    void getBoolean_customDefault_whenMissing() {
        when(repository.findById("unknown_key")).thenReturn(Optional.empty());
        assertTrue(service.getBoolean("unknown_key", true));
        assertFalse(service.getBoolean("unknown_key", false));
    }
}
