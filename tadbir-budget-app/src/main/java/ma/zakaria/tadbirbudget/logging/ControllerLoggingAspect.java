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
package ma.zakaria.tadbirbudget.logging;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single, cross-cutting request/response log for <b>every</b> {@code @RestController} endpoint —
 * no per-method boilerplate. For each call it logs the entry (arguments), the exit (HTTP status,
 * body and elapsed time) and any thrown exception.
 *
 * <p>Correlation (traceId, ip, username, method, uri) is already on every line via
 * {@link ma.zakaria.tadbirbudget.filter.MdcFilter} + Micrometer, so this aspect only adds the
 * call-specific payload.
 *
 * <p><b>Secrets are never logged.</b> Any field whose name looks like a credential
 * (password, token, jwt, secret…) is rendered as {@code ***}, and binary payloads
 * (files, streamed resources) are summarised rather than dumped.
 */
@Slf4j
@Aspect
@Component
public class ControllerLoggingAspect {

    /** Field names (lower-cased) whose values must never reach the logs. */
    private static final Set<String> SECRET_FIELDS = Set.of(
            "password", "currentpassword", "newpassword", "oldpassword",
            "jwt", "token", "accesstoken", "refreshtoken", "secret", "credential");

    /** Hard cap so a large body can never flood the logs. */
    private static final int MAX_LEN = 2_000;

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restControllers() { }

    @Around("restControllers()")
    public Object logEndpoint(ProceedingJoinPoint pjp) throws Throwable {
        String endpoint = pjp.getSignature().getDeclaringType().getSimpleName()
                + "." + pjp.getSignature().getName();

        log.info("API IN  {}({})", endpoint, formatArgs(pjp.getArgs()));
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("API OUT {} [{} ms] {}", endpoint, System.currentTimeMillis() - start, formatResult(result));
            return result;
        } catch (Throwable ex) {
            log.warn("API ERR {} [{} ms] {}: {}", endpoint, System.currentTimeMillis() - start,
                    ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    // ── Formatting ───────────────────────────────────────────────────────────────

    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        return Arrays.stream(args).map(this::render).collect(Collectors.joining(", "));
    }

    private String formatResult(Object result) {
        if (result instanceof ResponseEntity<?> re) {
            return "status=" + re.getStatusCode().value() + " body=" + render(re.getBody());
        }
        return render(result);
    }

    /** Renders one value safely: framework/binary types summarised, app DTOs field-redacted. */
    private String render(Object value) {
        if (value == null)                       return "null";
        if (value instanceof ServletRequest)     return "<request>";
        if (value instanceof ServletResponse)    return "<response>";
        if (value instanceof Resource)           return "<stream>";
        if (value instanceof byte[] b)           return "<binary " + b.length + "B>";
        if (value instanceof MultipartFile f)    return "File(" + f.getOriginalFilename() + ", " + f.getSize() + "B)";
        if (value instanceof Iterable<?> || value instanceof java.util.Map) {
            return cap(String.valueOf(value)); // collections: rely on element toString, capped
        }
        if (value.getClass().getName().startsWith("ma.zakaria.tadbirbudget")) {
            return renderAppObject(value);
        }
        return cap(String.valueOf(value));
    }

    /** Reflectively render an in-house DTO/entity, masking secret-looking fields. */
    private String renderAppObject(Object value) {
        StringBuilder sb = new StringBuilder(value.getClass().getSimpleName()).append('(');
        boolean first = true;
        for (Field field : value.getClass().getDeclaredFields()) {
            if (field.isSynthetic()) continue;
            if (!first) sb.append(", ");
            first = false;
            sb.append(field.getName()).append('=');
            if (SECRET_FIELDS.contains(field.getName().toLowerCase())) {
                sb.append("***");
                continue;
            }
            try {
                field.setAccessible(true);
                Object fieldValue = field.get(value);
                sb.append(fieldValue == null ? "null" : cap(String.valueOf(fieldValue)));
            } catch (Exception e) {
                sb.append("?");
            }
        }
        return cap(sb.append(')').toString());
    }

    private String cap(String s) {
        return s.length() <= MAX_LEN ? s : s.substring(0, MAX_LEN) + "…(" + s.length() + " chars)";
    }
}
