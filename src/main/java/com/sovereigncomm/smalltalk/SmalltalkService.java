package com.sovereigncomm.smalltalk;

import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class SmalltalkService {
    private final SmalltalkEngine engine = new SmalltalkEngine();

    public Object evaluate(String script, Map<String, Object> context) {
        return engine.evaluate(script, context);
    }
}
