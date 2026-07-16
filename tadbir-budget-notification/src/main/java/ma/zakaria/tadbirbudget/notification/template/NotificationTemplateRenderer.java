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
package ma.zakaria.tadbirbudget.notification.template;

import ma.zakaria.tadbirbudget.entity.enums.NotificationChannel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * Turns a {@link NotificationTemplate} + a model map into the concrete subject/body for
 * a given channel. MAIL gets the rich HTML file; APPLICATION gets the short inline text.
 */
@Component
public class NotificationTemplateRenderer {

    private final TemplateEngine htmlEngine;
    private final TemplateEngine textEngine;

    public NotificationTemplateRenderer(@Qualifier("notificationHtmlEngine") TemplateEngine htmlEngine,
                                        @Qualifier("notificationTextEngine") TemplateEngine textEngine) {
        this.htmlEngine = htmlEngine;
        this.textEngine = textEngine;
    }

    /** Render the template for one channel into a ready-to-store message. */
    public RenderedMessage render(NotificationTemplate template, NotificationChannel channel,
                                  Map<String, Object> model) {
        Context context = new Context();
        if (model != null) {
            context.setVariables(model);
        }
        String subject = textEngine.process(template.getSubject(), context).trim();
        if (channel == NotificationChannel.MAIL) {
            String html = htmlEngine.process(template.getMailTemplate(), context);
            return new RenderedMessage(subject, html, true);
        }
        String text = textEngine.process(template.getAppBody(), context).trim();
        return new RenderedMessage(subject, text, false);
    }
}
