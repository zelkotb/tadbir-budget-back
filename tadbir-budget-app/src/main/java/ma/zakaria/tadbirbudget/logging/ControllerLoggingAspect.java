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
import java.lang.reflect.Modifier;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
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
 * <p><b>Safe by construction:</b>
 * <ul>
 *   <li><b>Secrets</b> — any field whose name <i>contains</i> a credential word (password, secret,
 *       token, jwt, apiKey, clientSecret, credential…) or <i>is</i> a short secret (otp, pin, cvv…)
 *       renders as {@code ***}. Map keys are checked too.</li>
 *   <li><b>Long values</b> — a value longer than {@link #MAX_VALUE_LEN} chars (e.g. a big
 *       {@code description}) is never dumped; it is summarised as {@code <text N chars>}.</li>
 *   <li><b>Structure</b> — collections, maps and nested in-house DTOs are rendered recursively
 *       <i>through the same masking</i> (capped in width and depth), so a secret nested inside a
 *       list element or map is still hidden. Binary/streamed payloads are summarised, never dumped.</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class ControllerLoggingAspect {

    /** Credential markers matched as substrings of the (lower-cased) field/key name. */
    private static final Set<String> SECRET_SUBSTRINGS = Set.of(
            "password", "passwd", "secret", "token", "jwt",
            "credential", "apikey", "privatekey", "authorization");

    /** Short credential markers matched as whole words (so "shipping" isn't caught by "pin"). */
    private static final Set<String> SECRET_WORDS = Set.of(
            "otp", "totp", "mfa", "pin", "cvv", "ssn", "cookie");

    /** A single value longer than this is summarised (length only), never printed. */
    private static final int MAX_VALUE_LEN = 256;
    /** Max elements shown from a collection/map. */
    private static final int MAX_ITEMS = 20;
    /** Max nesting depth before we stop descending. */
    private static final int MAX_DEPTH = 4;
    /** Overall backstop so one line can never flood the logs. */
    private static final int MAX_LINE_LEN = 2_000;

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restControllers() { }

    @Around("restControllers()")
    public Object logEndpoint(ProceedingJoinPoint pjp) throws Throwable {
        String endpoint = pjp.getSignature().getDeclaringType().getSimpleName()
                + "." + pjp.getSignature().getName();

        log.info("API IN  {}({})", endpoint, capLine(formatArgs(pjp.getArgs())));
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("API OUT {} [{} ms] {}", endpoint, System.currentTimeMillis() - start,
                    capLine(formatResult(result)));
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
        return Arrays.stream(args).map(a -> render(a, 0)).collect(Collectors.joining(", "));
    }

    private String formatResult(Object result) {
        if (result instanceof ResponseEntity<?> re) {
            return "status=" + re.getStatusCode().value() + " body=" + render(re.getBody(), 0);
        }
        return render(result, 0);
    }

    /** Renders one value safely: binary types summarised, long values elided, app DTOs field-masked. */
    private String render(Object value, int depth) {
        if (value == null)                    return "null";
        if (value instanceof ServletRequest)  return "<request>";
        if (value instanceof ServletResponse) return "<response>";
        if (value instanceof Resource)        return "<stream>";
        if (value instanceof byte[] b)        return "<binary " + b.length + "B>";
        if (value instanceof MultipartFile f) return "File(" + f.getOriginalFilename() + ", " + f.getSize() + "B)";
        if (value instanceof CharSequence cs) return renderScalar(cs.toString());
        if (value instanceof Number || value instanceof Boolean || value instanceof Character
                || value instanceof UUID || value instanceof Temporal || value instanceof Enum<?>) {
            return renderScalar(String.valueOf(value));
        }
        if (depth >= MAX_DEPTH)               return "<…>";
        if (value instanceof Map<?, ?> m)     return renderMap(m, depth);
        if (value instanceof Iterable<?> it)  return renderIterable(it, depth);
        if (value instanceof Object[] arr)    return renderIterable(Arrays.asList(arr), depth);
        if (value.getClass().getName().startsWith("ma.zakaria.tadbirbudget")) {
            return renderAppObject(value, depth);
        }
        return renderScalar(String.valueOf(value));
    }

    /** Reflectively render an in-house DTO/entity, masking secret-looking fields. */
    private String renderAppObject(Object value, int depth) {
        StringBuilder sb = new StringBuilder(value.getClass().getSimpleName()).append('(');
        boolean first = true;
        for (Field field : value.getClass().getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!first) sb.append(", ");
            first = false;
            sb.append(field.getName()).append('=');
            if (looksSecret(field.getName())) {
                sb.append("***");
                continue;
            }
            try {
                field.setAccessible(true);
                sb.append(render(field.get(value), depth + 1));
            } catch (Exception e) {
                sb.append('?');
            }
        }
        return sb.append(')').toString();
    }

    private String renderIterable(Iterable<?> it, int depth) {
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (Object e : it) {
            if (i >= MAX_ITEMS) { sb.append(", …+more"); break; }
            if (i++ > 0) sb.append(", ");
            sb.append(render(e, depth + 1));
        }
        return sb.append(']').toString();
    }

    private String renderMap(Map<?, ?> map, int depth) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (i >= MAX_ITEMS) { sb.append(", …+more"); break; }
            if (i++ > 0) sb.append(", ");
            String key = String.valueOf(entry.getKey());
            sb.append(key).append('=')
                    .append(looksSecret(key) ? "***" : render(entry.getValue(), depth + 1));
        }
        return sb.append('}').toString();
    }

    /** Show a scalar/string value, or just its length when it is too long to log. */
    private String renderScalar(String s) {
        return s.length() <= MAX_VALUE_LEN ? s : "<text " + s.length() + " chars>";
    }

    // ── Secret detection ───────────────────────────────────────────────────────────

    private boolean looksSecret(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase();
        if (SECRET_SUBSTRINGS.stream().anyMatch(lower::contains)) {
            return true;
        }
        Set<String> words = Arrays.stream(
                        CAMEL_BOUNDARY.matcher(name).replaceAll("$1 $2")
                                .replace('_', ' ').replace('-', ' ')
                                .toLowerCase().split(" +"))
                .filter(w -> !w.isEmpty())
                .collect(Collectors.toSet());
        return SECRET_WORDS.stream().anyMatch(words::contains);
    }

    private String capLine(String s) {
        return s.length() <= MAX_LINE_LEN ? s : s.substring(0, MAX_LINE_LEN) + "…(" + s.length() + " chars)";
    }
}
