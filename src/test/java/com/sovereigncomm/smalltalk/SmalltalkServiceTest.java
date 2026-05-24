package com.sovereigncomm.smalltalk;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SmalltalkServiceTest {

    private final SmalltalkService service = new SmalltalkService();

    @Test
    void evaluatesBasicLiterals() {
        Map<String, Object> ctx = new HashMap<>();
        assertThat(service.evaluate("true", ctx)).isEqualTo(true);
        assertThat(service.evaluate("false", ctx)).isEqualTo(false);
        assertThat(service.evaluate("'hello'", ctx)).isEqualTo("hello");
        assertThat(service.evaluate("42", ctx)).isEqualTo(42);
    }

    @Test
    void evaluatesVariableLookupAndUnaryMessages() {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "AUDIT_EXPORT");
        event.put("platform", "Android");

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("event", event);

        assertThat(service.evaluate("event type", ctx)).isEqualTo("AUDIT_EXPORT");
        assertThat(service.evaluate("event platform", ctx)).isEqualTo("Android");
    }

    @Test
    void evaluatesBinaryOperators() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("x", 10);
        ctx.put("y", "iOS");

        assertThat(service.evaluate("x = 10", ctx)).isEqualTo(true);
        assertThat(service.evaluate("x > 5", ctx)).isEqualTo(true);
        assertThat(service.evaluate("y = 'iOS'", ctx)).isEqualTo(true);
        assertThat(service.evaluate("y contains 'OS'", ctx)).isEqualTo(true);
    }

    @Test
    void evaluatesBlocks() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("status", "ACTIVE");

        SmalltalkEngine.SmalltalkBlock block = (SmalltalkEngine.SmalltalkBlock) service.evaluate("[ :x | x = 'ACTIVE' ]", ctx);
        assertThat(block).isNotNull();
        assertThat(block.value("ACTIVE")).isEqualTo(true);
        assertThat(block.value("SUSPENDED")).isEqualTo(false);
    }
}
