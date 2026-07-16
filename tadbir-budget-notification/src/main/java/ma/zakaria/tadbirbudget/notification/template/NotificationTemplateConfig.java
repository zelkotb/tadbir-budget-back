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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.nio.charset.StandardCharsets;

/**
 * Two dedicated Thymeleaf engines for notifications (kept separate from any MVC
 * Thymeleaf so view rendering and e-mail rendering never interfere):
 * <ul>
 *   <li>{@code notificationHtmlEngine} — resolves rich HTML e-mail bodies from files
 *       under {@code resources/templates/notification/mail/*.html};</li>
 *   <li>{@code notificationTextEngine} — renders short inline TEXT templates (subjects
 *       and in-app bodies) supplied as strings by {@link NotificationTemplate}.</li>
 * </ul>
 */
@Configuration
public class NotificationTemplateConfig {

    @Bean
    public TemplateEngine notificationHtmlEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/notification/mail/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    @Bean
    public TemplateEngine notificationTextEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCacheable(false); // template "name" IS the content — nothing to cache
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
