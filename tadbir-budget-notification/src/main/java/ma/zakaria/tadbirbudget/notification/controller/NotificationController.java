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
package ma.zakaria.tadbirbudget.notification.controller;

import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.entity.User;
import ma.zakaria.tadbirbudget.notification.dto.NotificationView;
import ma.zakaria.tadbirbudget.notification.service.NotificationQueryService;
import ma.zakaria.tadbirbudget.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * The current user's in-app notification bell. (E-mail notifications need no API —
 * they land in the user's mailbox.)
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService queryService;

    /** GET /api/v1/notifications — my in-app notifications, newest first (paged). */
    @GetMapping
    public ResponseEntity<Page<NotificationView>> inbox(Pageable pageable) {
        return ResponseEntity.ok(queryService.inbox(currentUserId(), pageable));
    }

    /** GET /api/v1/notifications/unread-count — badge count for the bell. */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("count", queryService.unreadCount(currentUserId())));
    }

    /** POST /api/v1/notifications/{id}/read — mark one as read. */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        queryService.markRead(currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/v1/notifications/read-all — mark all my unread in-app messages as read. */
    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        return ResponseEntity.ok(Map.of("marked", queryService.markAllRead(currentUserId())));
    }

    private UUID currentUserId() {
        return ((User) SecurityUtils.getCurrentUser()).getId();
    }
}
