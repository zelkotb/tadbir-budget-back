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
package ma.zakaria.tadbirbudget.notification.channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.entity.Notification;
import ma.zakaria.tadbirbudget.entity.enums.NotificationChannel;
import jakarta.mail.internet.MimeMessage;
import ma.zakaria.tadbirbudget.notification.config.NotificationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Sends notifications by e-mail through Spring's {@link JavaMailSender}.
 *
 * <p>If {@code notification.mail.enabled=false} (or no SMTP server is configured),
 * the message is logged instead of sent — so the rest of the system runs unchanged
 * in dev/test without a mail server.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailChannelSender implements NotificationChannelSender {

    private final NotificationProperties properties;
    /** ObjectProvider: a JavaMailSender bean only exists when SMTP is configured. */
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.MAIL;
    }

    @Override
    public void send(Notification notification) throws Exception {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (!properties.getMail().isEnabled() || sender == null) {
            log.info("[mail disabled] would send to={} subject=\"{}\"",
                    notification.getRecipientEmail(), notification.getSubject());
            return; // treated as a successful delivery — nothing to retry
        }
        if (notification.isHtml()) {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.getMail().getFrom());
            helper.setTo(notification.getRecipientEmail());
            helper.setSubject(notification.getSubject());
            helper.setText(notification.getBody(), true); // true = HTML
            sender.send(mime); // throws MailException on failure → dispatcher retries
        } else {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.getMail().getFrom());
            message.setTo(notification.getRecipientEmail());
            message.setSubject(notification.getSubject());
            message.setText(notification.getBody());
            sender.send(message);
        }
        log.debug("Mail sent to={} subject=\"{}\" html={}",
                notification.getRecipientEmail(), notification.getSubject(), notification.isHtml());
    }
}
