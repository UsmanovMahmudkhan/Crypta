package com.sovereigncomm.cql;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CqlPolicyServiceTest {

    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final CqlPolicyService service = new CqlPolicyService(jdbcTemplate);

    @Test
    void parsesSelectAllFromAuditEvents() {
        CqlQuery query = service.parse("SELECT * FROM AUDIT_EVENTS");
        assertThat(query.getSource()).isEqualTo("AUDIT_EVENTS");
        assertThat(query.isAllFields()).isTrue();
        assertThat(query.getFilterField()).isNull();
    }

    @Test
    void parsesSpecificFieldsWithWhereClause() {
        CqlQuery query = service.parse("SELECT id, event_type FROM DEVICES WHERE platform = 'iOS'");
        assertThat(query.getSource()).isEqualTo("DEVICES");
        assertThat(query.isAllFields()).isFalse();
        assertThat(query.getFields()).containsExactly("id", "event_type");
        assertThat(query.getFilterField()).isEqualTo("platform");
        assertThat(query.getFilterOperator()).isEqualTo("=");
        assertThat(query.getFilterValue()).isEqualTo("iOS");
    }

    @Test
    void parsesWhereClauseWithContainsOperator() {
        CqlQuery query = service.parse("SELECT * FROM ROOMS WHERE name CONTAINS 'Secret'");
        assertThat(query.getSource()).isEqualTo("ROOMS");
        assertThat(query.getFilterField()).isEqualTo("name");
        assertThat(query.getFilterOperator()).isEqualTo("CONTAINS");
        assertThat(query.getFilterValue()).isEqualTo("Secret");
    }

    @Test
    void throwsExceptionOnInvalidSyntax() {
        assertThatThrownBy(() -> service.parse("SELECT FROM AUDIT_EVENTS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid CQL");
    }
}
