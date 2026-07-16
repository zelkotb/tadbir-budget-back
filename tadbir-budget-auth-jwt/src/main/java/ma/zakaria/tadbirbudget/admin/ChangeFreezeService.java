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
package ma.zakaria.tadbirbudget.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.entity.ChangeFreeze;
import ma.zakaria.tadbirbudget.repository.ChangeFreezeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The global change-freeze switch (single row). A generic maintenance flag an admin can toggle:
 * when frozen, ROLE_USER accounts may not sign up (the signup block is enforced in the auth
 * module, which reads the same row). Admins are never blocked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeFreezeService {

    private final ChangeFreezeRepository repository;

    @Transactional(readOnly = true)
    public boolean isFrozen() {
        return repository.findById(ChangeFreeze.SINGLETON_ID)
                .map(ChangeFreeze::isFrozen)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public ChangeFreezeOutput current() {
        return repository.findById(ChangeFreeze.SINGLETON_ID)
                .map(c -> new ChangeFreezeOutput(c.isFrozen(), c.getUpdatedAt(), c.getUpdatedBy()))
                .orElse(new ChangeFreezeOutput(false, null, null));
    }

    @Transactional
    public ChangeFreezeOutput setFrozen(boolean frozen, UUID adminId) {
        ChangeFreeze freeze = repository.findById(ChangeFreeze.SINGLETON_ID)
                .orElseGet(() -> ChangeFreeze.builder().id(ChangeFreeze.SINGLETON_ID).build());
        freeze.setFrozen(frozen);
        freeze.setUpdatedAt(Instant.now());
        freeze.setUpdatedBy(adminId);
        repository.save(freeze);
        log.info("Change-freeze set to {} by {}", frozen, adminId);
        return new ChangeFreezeOutput(freeze.isFrozen(), freeze.getUpdatedAt(), freeze.getUpdatedBy());
    }
}
