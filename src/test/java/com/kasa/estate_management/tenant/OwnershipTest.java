package com.kasa.estate_management.tenant;

import com.kasa.estate_management.auth.model.Role;
import com.kasa.estate_management.support.ApiTestSupport;
import com.kasa.estate_management.support.TestApi.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OwnershipTest extends ApiTestSupport {

    private static final String COMPLAINT =
        "{\"category\":\"SECURITY\",\"title\":\"Gate lock\",\"description\":\"The gate lock is broken.\"}";

    @Test
    void aTenantSeesOnlyTheirOwnInvoices() {
        TenantFixture a = tenantWithInvoice("10000");
        TenantFixture b = tenantWithInvoice("12000");

        Response list = api().send("GET", "/api/v1/me/invoices", a.token(), null);
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body())
            .contains(a.invoiceId().toString())
            .doesNotContain(b.invoiceId().toString());

        assertThat(api().send("GET", "/api/v1/me/invoices/" + a.invoiceId(), a.token(), null).status())
            .isEqualTo(200);
    }

    @Test
    void aTenantCannotReadOrPayAnotherTenantsInvoice() {
        TenantFixture a = tenantWithInvoice("10000");
        TenantFixture b = tenantWithInvoice("12000");
        String theirs = "/api/v1/me/invoices/" + b.invoiceId();

        Response read = api().send("GET", theirs, a.token(), null);
        assertThat(read.status()).isEqualTo(400);
        assertThat(read.body()).contains("Invoice not found");

        assertThat(api().send("GET", theirs + "/payments", a.token(), null).status()).isEqualTo(400);

        // M-Pesa is not configured in tests, so a missing ownership check would show up as 503 here.
        assertThat(api().send("POST", theirs + "/pay", a.token(), null).status()).isEqualTo(400);

        long payments = jdbc().sql("SELECT count(*) FROM payments WHERE invoice_id = :id")
            .param("id", b.invoiceId())
            .query(Long.class)
            .single();
        assertThat(payments).isZero();
    }

    @Test
    void aTenantCannotReadAnotherTenantsComplaint() {
        TenantFixture a = tenantWithInvoice("10000");
        TenantFixture b = tenantWithInvoice("12000");

        Response created = api().send("POST", "/api/v1/me/complaints", b.token(), COMPLAINT);
        assertThat(created.status()).isEqualTo(201);
        String complaintId = created.field("id");

        assertThat(api().send("GET", "/api/v1/me/complaints/" + complaintId, b.token(), null).status())
            .isEqualTo(200);

        Response stolen = api().send("GET", "/api/v1/me/complaints/" + complaintId, a.token(), null);
        assertThat(stolen.status()).isEqualTo(400);
        assertThat(stolen.body()).contains("Complaint not found");

        Response list = api().send("GET", "/api/v1/me/complaints", a.token(), null);
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).doesNotContain(complaintId);
    }

    @Test
    void aTenantCannotRaiseAComplaintForAUnitTheyDoNotLease() {
        TenantFixture a = tenantWithInvoice("10000");
        TenantFixture b = tenantWithInvoice("12000");

        String forSomeoneElsesUnit = "{\"unitId\":\"" + b.unitId() + "\",\"category\":\"SECURITY\","
            + "\"title\":\"Not mine\",\"description\":\"This is not my unit.\"}";
        Response refused = api().send("POST", "/api/v1/me/complaints", a.token(), forSomeoneElsesUnit);
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("does not currently lease");

        String lonelyToken = tenantLoginWithoutLease();
        Response noLease = api().send("POST", "/api/v1/me/complaints", lonelyToken, COMPLAINT);
        assertThat(noLease.status()).isEqualTo(400);
        assertThat(noLease.body()).contains("no active lease");
    }

    @Test
    void aCaretakerCanOnlyWorkComplaintsAssignedToThem() {
        TenantFixture tenant = tenantWithInvoice("10000");
        Staff admin = staff(Role.ADMIN);
        Staff mine = staff(Role.CARETAKER);
        Staff other = staff(Role.CARETAKER);

        String complaintId = api().send("POST", "/api/v1/me/complaints", tenant.token(), COMPLAINT).field("id");
        assertThat(complaintId).isNotNull();
        String path = "/api/v1/complaints/" + complaintId;

        // Not assigned yet, so no caretaker may touch it.
        assertThat(api().send("PATCH", path + "/status", mine.token(), "{\"status\":\"IN_PROGRESS\"}").status())
            .isEqualTo(403);

        // A caretaker cannot assign work; an admin can.
        String assignToMine = "{\"staffUserId\":\"" + mine.id() + "\"}";
        assertThat(api().send("PATCH", path + "/assign", mine.token(), assignToMine).status()).isEqualTo(403);
        assertThat(api().send("PATCH", path + "/assign", admin.token(), assignToMine).status()).isEqualTo(200);

        // A different caretaker still cannot touch it.
        assertThat(api().send("PATCH", path + "/status", other.token(),
            "{\"status\":\"RESOLVED\",\"notes\":\"I did it\"}").status()).isEqualTo(403);

        // The assignee must say what was done.
        assertThat(api().send("PATCH", path + "/status", mine.token(), "{\"status\":\"RESOLVED\"}").status())
            .isEqualTo(400);
        assertThat(api().send("PATCH", path + "/status", mine.token(),
            "{\"status\":\"RESOLVED\",\"notes\":\"Replaced the lock\"}").status()).isEqualTo(200);

        // But closing is for a manager or admin.
        assertThat(api().send("PATCH", path + "/status", mine.token(), "{\"status\":\"CLOSED\"}").status())
            .isEqualTo(403);
        assertThat(api().send("PATCH", path + "/status", admin.token(), "{\"status\":\"CLOSED\"}").status())
            .isEqualTo(200);

        // Closed is final.
        assertThat(api().send("PATCH", path + "/status", admin.token(), "{\"status\":\"IN_PROGRESS\"}").status())
            .isEqualTo(400);
    }
}