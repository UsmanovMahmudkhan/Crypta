package com.sovereigncomm.api;

import com.sovereigncomm.cql.CqlPolicyService;
import com.sovereigncomm.cql.CqlQuery;
import com.sovereigncomm.security.PlaintextGuard;
import com.sovereigncomm.smalltalk.SmalltalkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/governance")
public class GovernanceController {

    private final CqlPolicyService cqlPolicyService;
    private final SmalltalkService smalltalkService;
    private final PlaintextGuard plaintextGuard;
    private final boolean smalltalkEnabled;
    private final int maxSmalltalkScriptChars;

    public GovernanceController(CqlPolicyService cqlPolicyService, SmalltalkService smalltalkService,
                                PlaintextGuard plaintextGuard,
                                @Value("${app.governance.smalltalk.enabled:false}") boolean smalltalkEnabled,
                                @Value("${app.governance.smalltalk.max-script-chars:2000}") int maxSmalltalkScriptChars) {
        this.cqlPolicyService = cqlPolicyService;
        this.smalltalkService = smalltalkService;
        this.plaintextGuard = plaintextGuard;
        this.smalltalkEnabled = smalltalkEnabled;
        this.maxSmalltalkScriptChars = maxSmalltalkScriptChars;
    }

    @PostMapping("/cql/parse")
    public CqlQuery parseCql(@RequestBody String query) {
        return cqlPolicyService.parse(query);
    }

    @PostMapping("/cql/execute")
    public List<Map<String, Object>> executeCql(@RequestBody String query) {
        return cqlPolicyService.execute(query);
    }

    @PostMapping("/smalltalk/evaluate")
    public Object evaluateSmalltalk(@RequestBody SmalltalkEvaluateRequest request) {
        if (!smalltalkEnabled) {
            throw new SecurityException("Smalltalk governance evaluation is disabled for this runtime profile");
        }
        if (request.getScript() == null || request.getScript().length() > maxSmalltalkScriptChars) {
            throw new IllegalArgumentException("Smalltalk script exceeds configured execution boundary");
        }
        plaintextGuard.rejectPlaintextShapedMetadata(request.getContext());
        Object result = smalltalkService.evaluate(request.getScript(), request.getContext());
        if (result instanceof Map<?, ?> raw) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            raw.forEach((k, v) -> mapped.put(String.valueOf(k), v));
            plaintextGuard.rejectPlaintextShapedMetadata(mapped);
        }
        return result;
    }

    public static class SmalltalkEvaluateRequest {
        private String script;
        private Map<String, Object> context;

        public String getScript() {
            return script;
        }

        public void setScript(String script) {
            this.script = script;
        }

        public Map<String, Object> getContext() {
            return context;
        }

        public void setContext(Map<String, Object> context) {
            this.context = context;
        }
    }
}
