package com.sovereigncomm.cql;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

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

    @Test
    void executeScopesQueriesWithoutExistingWhereClause() {
        UUID organizationId = UUID.randomUUID();
        Mockito.when(jdbcTemplate.queryForList(Mockito.anyString(), Mockito.any(Object[].class))).thenReturn(List.of());

        service.execute("SELECT id FROM DEVICES", organizationId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        Mockito.verify(jdbcTemplate).queryForList(sql.capture(), params.capture());
        assertThat(sql.getValue()).isEqualTo("SELECT id FROM devices WHERE organization_id = ? LIMIT ?");
        assertThat(params.getValue()).containsExactly(organizationId, 100);
    }

    @Test
    void executeScopesQueriesWithExistingWhereClause() {
        UUID organizationId = UUID.randomUUID();
        Mockito.when(jdbcTemplate.queryForList(Mockito.anyString(), Mockito.any(Object[].class))).thenReturn(List.of());

        service.execute("SELECT id FROM AUDIT_EVENTS WHERE event_type = 'LOGIN'", organizationId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        Mockito.verify(jdbcTemplate).queryForList(sql.capture(), params.capture());
        assertThat(sql.getValue()).isEqualTo("SELECT id FROM audit_events WHERE event_type = ? AND organization_id = ? LIMIT ?");
        assertThat(params.getValue()).containsExactly("LOGIN", organizationId, 100);
    }

    @Test
    void executeUsesParameterizedContainsFilterWithOrgScope() {
        UUID organizationId = UUID.randomUUID();
        Mockito.when(jdbcTemplate.queryForList(Mockito.anyString(), Mockito.any(Object[].class))).thenReturn(List.of());

        service.execute("SELECT id FROM ROOMS WHERE name CONTAINS 'Secret'", organizationId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        Mockito.verify(jdbcTemplate).queryForList(sql.capture(), params.capture());
        assertThat(sql.getValue()).isEqualTo("SELECT id FROM rooms WHERE name LIKE ? AND organization_id = ? LIMIT ?");
        assertThat(params.getValue()).containsExactly("%Secret%", organizationId, 100);
    }

    @Test
    void executeUsesParameterizedInequalityFilterWithOrgScope() {
        UUID organizationId = UUID.randomUUID();
        Mockito.when(jdbcTemplate.queryForList(Mockito.anyString(), Mockito.any(Object[].class))).thenReturn(List.of());

        service.execute("SELECT id FROM DEVICES WHERE trust_state != 'REVOKED'", organizationId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        Mockito.verify(jdbcTemplate).queryForList(sql.capture(), params.capture());
        assertThat(sql.getValue()).isEqualTo("SELECT id FROM devices WHERE trust_state != ? AND organization_id = ? LIMIT ?");
        assertThat(params.getValue()).containsExactly("REVOKED", organizationId, 100);
    }

    @Test
    void rejectsNullOrganizationScopeForExecution() {
        assertThatThrownBy(() -> service.execute("SELECT id FROM DEVICES", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("organization scope");
    }

    @Test
    void rejectsDisallowedColumnsAndSources() {
        assertThatThrownBy(() -> service.execute("SELECT metadata FROM AUDIT_EVENTS", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
        assertThatThrownBy(() -> service.execute("SELECT id FROM USERS", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid CQL");
    }

    @Test
    void enforcesQueryLengthLimit() {
        CqlPolicyService limited = new CqlPolicyService(jdbcTemplate, 10, 100);

        assertThatThrownBy(() -> limited.parse("SELECT * FROM AUDIT_EVENTS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length limit");
    }

    @Test
    void enforcesConfiguredResultCap() {
        UUID organizationId = UUID.randomUUID();
        CqlPolicyService capped = new CqlPolicyService(jdbcTemplate, 2000, 7);
        Mockito.when(jdbcTemplate.queryForList(Mockito.anyString(), Mockito.any(Object[].class))).thenReturn(List.of());

        capped.execute("SELECT id FROM ROOMS", organizationId);

        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        Mockito.verify(jdbcTemplate).queryForList(Mockito.anyString(), params.capture());
        assertThat(params.getValue()).containsExactly(organizationId, 7);
    }
}
