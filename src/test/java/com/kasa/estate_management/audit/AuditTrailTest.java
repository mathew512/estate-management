package com.kasa.estate_management.audit;

import com.kasa.estate_management.auth.model.Role;
import com.kasa.estate_management.support.ApiTestSupport;
import com.kasa.estate_management.support.TestApi.Response;
import com.kasa.estate_management.tenant.model.Tenant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditTrailTest extends ApiTestSupport {

    private static final String SECRET = "S3cretWrongPw";

    @Test
    void aRefusedLeaseLeavesNoAuditEntry() {
        TenantFixture occupied = tenantWithInvoice("10000");
        Tenant newcomer = tenantService.onboardTenant("Newcomer", randomPhone(), null, null);

        long before = countAudit("LEASE_CREATED");

        assertThatThrownBy(() -> leaseService.createLease(
            occupied.unitId(), newcomer.getId(), NOV, new BigDecimal("10000"), BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already has an active lease");

        assertThat(countAudit("LEASE_CREATED")).isEqualTo(before);
    }

    @Test
    void theAuditLogCannotBeChangedOrDeleted() {
        staff(Role.ACCOUNTANT); // makes sure at least one audit row exists

        assertThatThrownBy(() -> jdbc().sql("UPDATE audit_log SET summary = 'tampered'").update())
            .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc().sql("DELETE FROM audit_log").update())
            .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc().sql("TRUNCATE audit_log").update())
            .hasMessageContaining("append-only");
    }

    @Test
    void failedLoginsAreRecordedWithoutThePassword() {
        String email = "nobody." + shortId() + "@example.com";

        Response response = api().send("POST", "/api/v1/auth/login", null,
            "{\"email\":\"" + email + "\",\"password\":\"" + SECRET + "\"}");
        assertThat(response.status()).isEqualTo(400);

        long recorded = jdbc()
            .sql("SELECT count(*) FROM audit_log WHERE action = 'LOGIN_FAILED' AND actor_label = :email")
            .param("email", email)
            .query(Long.class)
            .single();
        assertThat(recorded).isEqualTo(1);

        long leaked = jdbc()
            .sql("SELECT count(*) FROM audit_log WHERE summary LIKE :p OR actor_label LIKE :p")
            .param("p", "%" + SECRET + "%")
            .query(Long.class)
            .single();
        assertThat(leaked).isZero();
    }

    @Test
    void anAdminsChangeIsAttributedToThem() {
        Staff admin = staff(Role.ADMIN);

        Response created = api().send("POST", "/api/v1/properties", admin.token(),
            "{\"name\":\"Attribution Plaza " + shortId() + "\",\"location\":\"Nairobi\"}");
        assertThat(created.status()).isEqualTo(201);
        String propertyId = created.field("id");

        String actor = jdbc()
            .sql("SELECT actor_label || '|' || actor_role FROM audit_log "
                + "WHERE entity_id = :id AND action = 'PROPERTY_CREATED'")
            .param("id", propertyId)
            .query(String.class)
            .single();
        assertThat(actor).isEqualTo(admin.email() + "|ADMIN");
    }
}