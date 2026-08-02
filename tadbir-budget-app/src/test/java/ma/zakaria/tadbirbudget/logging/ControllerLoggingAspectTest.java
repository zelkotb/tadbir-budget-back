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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the safe-rendering contract of {@link ControllerLoggingAspect} (secrets, long values, nesting). */
class ControllerLoggingAspectTest {

    private final ControllerLoggingAspect aspect = new ControllerLoggingAspect();

    private String render(Object value) throws Exception {
        Method m = ControllerLoggingAspect.class.getDeclaredMethod("render", Object.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(aspect, value, 0);
    }

    @SuppressWarnings("unused")
    static class Sample {
        String name = "Alice";
        String password = "P@ss2026";
        String clientSecret = "cs-123";
        String apiKey = "ak-456";
        String mailPassword = "mp-789";
        String otp = "999000";
        String pin = "4242";
        String shippingAddress = "12 rue de la Marina";   // must NOT be masked (contains "pin")
        String settingKey = "project.terminology";        // must NOT be masked
        String description;
        Sample(String description) { this.description = description; }
    }

    @SuppressWarnings("unused")
    static class Member {
        String user = "bob";
        String password = "secret-in-list";
    }

    @SuppressWarnings("unused")
    static class WithList {
        String title = "team";
        List<Member> members = List.of(new Member(), new Member());
    }

    @Test
    void masksSecretsButKeepsNormalFields() throws Exception {
        String out = render(new Sample("short desc"));
        assertThat(out).contains("password=***", "clientSecret=***", "apiKey=***",
                "mailPassword=***", "otp=***", "pin=***");
        assertThat(out).contains("name=Alice", "description=short desc");
        // false positives avoided
        assertThat(out).contains("shippingAddress=12 rue de la Marina");
        assertThat(out).contains("settingKey=project.terminology");
    }

    @Test
    void elidesLongValuesInsteadOfDumping() throws Exception {
        String longDesc = "x".repeat(2314);
        String out = render(new Sample(longDesc));
        assertThat(out).contains("description=<text 2314 chars>");
        assertThat(out).doesNotContain("xxxxxxxxxx");   // raw content never printed
    }

    @Test
    void masksSecretsNestedInCollections() throws Exception {
        String out = render(new WithList());
        assertThat(out).contains("title=team");
        assertThat(out).contains("password=***");
        assertThat(out).doesNotContain("secret-in-list");
    }

    @Test
    void masksSecretMapKeys() throws Exception {
        String out = render(Map.of("apiKey", "leak-me"));
        assertThat(out).contains("apiKey=***");
        assertThat(out).doesNotContain("leak-me");
    }

    @Test
    void summarisesBinaryAndStreamValues() throws Exception {
        assertThat(render(new byte[1024])).isEqualTo("<binary 1024B>");
        assertThat(render(null)).isEqualTo("null");
    }
}
