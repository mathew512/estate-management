package com.kasa.estate_management.support;

import com.kasa.estate_management.auth.model.Role;
import com.kasa.estate_management.auth.model.User;
import com.kasa.estate_management.auth.service.UserService;
import com.kasa.estate_management.billing.model.Invoice;
import com.kasa.estate_management.billing.service.InvoiceService;
import com.kasa.estate_management.property.model.Property;
import com.kasa.estate_management.property.model.Unit;
import com.kasa.estate_management.property.service.PropertyService;
import com.kasa.estate_management.property.service.UnitService;
import com.kasa.estate_management.tenant.model.Lease;
import com.kasa.estate_management.tenant.model.Tenant;
import com.kasa.estate_management.tenant.service.LeaseService;
import com.kasa.estate_management.tenant.service.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Builds real users, tenants and leases for tests, with unique values so tests never collide. */
public abstract class ApiTestSupport extends IntegrationTestBase {

    protected static final String PASSWORD = "Password123!";
    protected static final LocalDate NOV = LocalDate.of(2026, 11, 1);

    @Autowired protected UserService userService;
    @Autowired protected TenantService tenantService;
    @Autowired protected PropertyService propertyService;
    @Autowired protected UnitService unitService;
    @Autowired protected LeaseService leaseService;
    @Autowired protected InvoiceService invoiceService;
    @Autowired protected DataSource dataSource;

    protected record Staff(UUID id, String email, String token) {}

    protected record TenantFixture(UUID tenantId, UUID unitId, UUID leaseId, UUID invoiceId,
                                   String email, String token) {}

    protected JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }

    protected Staff staff(Role role) {
        String email = role.name().toLowerCase() + "." + shortId() + "@example.com";
        User user = userService.registerUser("Test " + role, email, null, PASSWORD, role);
        return new Staff(user.getId(), email, api().login(email, PASSWORD));
    }

    /** A tenant with a login, a unit, an active lease and one November invoice. */
    protected TenantFixture tenantWithInvoice(String rent) {
        Property property = propertyService.createProperty("Test Plaza " + UUID.randomUUID(), "Nairobi");
        Unit unit = unitService.addUnit(property.getId(), "U1", new BigDecimal("12000"), null);
        Tenant tenant = tenantService.onboardTenant("Test Tenant", randomPhone(), null, null);

        String email = "tenant." + shortId() + "@example.com";
        tenantService.createLoginAccount(tenant.getId(), email, PASSWORD);

        Lease lease = leaseService.createLease(
            unit.getId(), tenant.getId(), NOV, new BigDecimal(rent), BigDecimal.ZERO);
        Invoice invoice = invoiceService.generateInvoice(lease.getId(), NOV, null);

        return new TenantFixture(tenant.getId(), unit.getId(), lease.getId(), invoice.getId(),
            email, api().login(email, PASSWORD));
    }

    /** A tenant who can log in but has no lease. Returns their token. */
    protected String tenantLoginWithoutLease() {
        Tenant tenant = tenantService.onboardTenant("Lonely Tenant", randomPhone(), null, null);
        String email = "lonely." + shortId() + "@example.com";
        tenantService.createLoginAccount(tenant.getId(), email, PASSWORD);
        return api().login(email, PASSWORD);
    }

    protected long countAudit(String action) {
        return jdbc().sql("SELECT count(*) FROM audit_log WHERE action = :action")
            .param("action", action)
            .query(Long.class)
            .single();
    }

    protected static String randomPhone() {
        return "07" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    protected static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}