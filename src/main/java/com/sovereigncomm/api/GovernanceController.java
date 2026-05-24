package com.sovereigncomm.api;

import com.sovereigncomm.cql.CqlPolicyService;
import com.sovereigncomm.cql.CqlQuery;
import com.sovereigncomm.smalltalk.SmalltalkService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/governance")
public class GovernanceController {

    private final CqlPolicyService cqlPolicyService;
    private final SmalltalkService smalltalkService;

    public GovernanceController(CqlPolicyService cqlPolicyService, SmalltalkService smalltalkService) {
        this.cqlPolicyService = cqlPolicyService;
        this.smalltalkService = smalltalkService;
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
        return smalltalkService.evaluate(request.getScript(), request.getContext());
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
