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
package ma.zakaria.tadbirbudget.notification.service;

import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.entity.Notification;
import ma.zakaria.tadbirbudget.entity.enums.NotificationChannel;
import ma.zakaria.tadbirbudget.entity.enums.NotificationStatus;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.notification.dto.NotificationView;
import ma.zakaria.tadbirbudget.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read/maintain the current user's in-app notification bell. */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository repository;

    @Transactional(readOnly = true)
    public Page<NotificationView> inbox(UUID userId, Pageable pageable) {
        return repository
                .findByRecipientIdAndChannelOrderByCreatedAtDesc(userId, NotificationChannel.APPLICATION, pageable)
                .map(NotificationView::from);
    }

    /** Count of delivered-but-unopened in-app messages (the bell badge). */
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByRecipientIdAndChannelAndStatus(
                userId, NotificationChannel.APPLICATION, NotificationStatus.SENT);
    }

    /** Mark one of the current user's in-app messages as read. */
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = repository.findById(notificationId)
                .filter(x -> x.getRecipientId().equals(userId)
                        && x.getChannel() == NotificationChannel.APPLICATION)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (n.getStatus() == NotificationStatus.SENT) {
            n.setStatus(NotificationStatus.READ);
            n.setReadAt(Instant.now());
            repository.save(n);
        }
    }

    /**
     * Mark every unread (SENT) in-app message of the user as read. Updated entity-by-entity
     * (not a bulk SQL UPDATE) so Hibernate Envers records each change in {@code notification_audit}.
     *
     * @return how many were marked
     */
    @Transactional
    public int markAllRead(UUID userId) {
        List<Notification> unread = repository.findByRecipientIdAndChannelAndStatus(
                userId, NotificationChannel.APPLICATION, NotificationStatus.SENT);
        Instant now = Instant.now();
        unread.forEach(n -> {
            n.setStatus(NotificationStatus.READ);
            n.setReadAt(now);
        });
        repository.saveAll(unread);
        return unread.size();
    }
}
