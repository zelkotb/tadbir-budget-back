/*
 * Copyright (c) 2026 Zakaria El Kotb. All rights reserved.
 *
 * This source code is the exclusive property of Zakaria El Kotb.
 * Unauthorized copying, modification, distribution, or use of this file,
 * via any medium, is strictly prohibited without the prior written
 * permission of the copyright owner.
 *
 * Author: Zakaria El Kotb <elkotbzakaria@gmail.com>
 */
package ma.zakaria.tadbirbudget.settings.service;

import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.entity.AppSetting;
import ma.zakaria.tadbirbudget.entity.enums.ProjectType;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.repository.AppSettingRepository;
import ma.zakaria.tadbirbudget.settings.SettingKeys;
import ma.zakaria.tadbirbudget.settings.dto.SettingResponse;
import ma.zakaria.tadbirbudget.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Company-wide settings (paramétrage). Keys are seeded (you can't create arbitrary ones); values
 * are validated per known key. Reads are open; updates are admin-only.
 */
@Service
@RequiredArgsConstructor
public class SettingService {

    private final AppSettingRepository settingRepository;

    @Transactional(readOnly = true)
    public List<SettingResponse> listAll() {
        return settingRepository.findAllByOrderByKeyAsc().stream().map(SettingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SettingResponse get(String key) {
        return SettingResponse.from(load(key));
    }

    @Transactional
    public SettingResponse update(String key, String value) {
        AppSetting s = load(key);
        String v = value.trim();
        validate(key, v);
        s.setValue(v);
        s.setUpdatedBy(SecurityUtils.getCurrentUsername());
        s.setUpdatedAt(Instant.now());
        return SettingResponse.from(settingRepository.save(s));
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private AppSetting load(String key) {
        return settingRepository.findById(key)
                .orElseThrow(() -> new CustomException(ErrorCode.SETTING_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /** Per-key value validation. */
    private void validate(String key, String value) {
        if (SettingKeys.PROJECT_TERMINOLOGY.equals(key)) {
            boolean ok = Arrays.stream(ProjectType.values()).anyMatch(t -> t.name().equals(value));
            if (!ok) {
                throw new CustomException(ErrorCode.SETTING_INVALID_VALUE, HttpStatus.BAD_REQUEST);
            }
        }
    }
}
