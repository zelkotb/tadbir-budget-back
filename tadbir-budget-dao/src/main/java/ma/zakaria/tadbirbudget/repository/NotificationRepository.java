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
package ma.zakaria.tadbirbudget.repository;

import ma.zakaria.tadbirbudget.entity.Notification;
import ma.zakaria.tadbirbudget.entity.enums.NotificationChannel;
import ma.zakaria.tadbirbudget.entity.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * The dispatcher's work queue: the oldest PENDING notifications on a channel that
     * are due to be (re)tried now. {@code Pageable} caps the batch size per tick.
     */
    List<Notification> findByChannelAndStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            NotificationChannel channel, NotificationStatus status, Instant now, Pageable pageable);

    /** The in-app notification bell, newest first. */
    Page<Notification> findByRecipientIdAndChannelOrderByCreatedAtDesc(
            UUID recipientId, NotificationChannel channel, Pageable pageable);

    /** Unread badge count: delivered (SENT) in-app messages the user has not opened yet. */
    long countByRecipientIdAndChannelAndStatus(
            UUID recipientId, NotificationChannel channel, NotificationStatus status);

    /** All in-app messages of a recipient in a given status — used by "mark all as read". */
    List<Notification> findByRecipientIdAndChannelAndStatus(
            UUID recipientId, NotificationChannel channel, NotificationStatus status);
}
