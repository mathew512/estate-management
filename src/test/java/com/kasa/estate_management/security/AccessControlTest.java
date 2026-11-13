package com.kasa.estate_management.security;

import com.kasa.estate_management.auth.model.Role;
import com.kasa.estate_management.support.ApiTestSupport;
import com.kasa.estate_management.support.TestApi.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.kasa.estate_management.auth.model.Role.ACCOUNTANT;
import static com.kasa.estate_management.auth.model.Role.ADMIN;
import static com.kasa.estate_management.auth.model.Role.CARETAKER;
import static com.kasa.estate_management.auth.model.Role.PROPERTY_MANAGER;
import static com.kasa.estate_management.auth.model.Role.TENANT;
import static org.assertj.core.api.Assertions.assertThat;

class AccessControlTest extends ApiTestSupport {

    private static final String ZERO = "00000000-0000-0000-0000-000000000000";

    /**
     * One endpoint and the roles allowed to use it. A denied caller must get exactly 403. An allowed
     * caller must get anything but 403 (the empty request then fails validation with 400, which is
     * fine, because only the security decision is being tested). Endpoints with side effects are
     * checked for denial only.
     */
    private record Rule(String method, String path, Set<Role> allowed, boolean checkAllowed) {

        static Rule of(String method, String path, Role... allowed) {
            Set<Role> roles = EnumSet.noneOf(Role.class);
            roles.addAll(Arrays.asList(allowed));
            return new Rule(method, path, roles, true);
        }

        Rule denialOnly() {
            return new Rule(method, path, allowed, false);
        }
    }

    private static final List<Rule> RULES = List.of(
        Rule.of("GET", "/api/v1/properties", ADMIN, PROPERTY_MANAGER, ACCOUNTANT, CARETAKER),
        Rule.of("POST", "/api/v1/properties", ADMIN, PROPERTY_MANAGER),
        Rule.of("GET", "/api/v1/units", ADMIN, PROPERTY_MANAGER, ACCOUNTANT, CARETAKER),
        Rule.of("GET", "/api/v1/tenants/" + ZERO, ADMIN, PROPERTY_MANAGER, ACCOUNTANT),
        Rule.of("POST", "/api/v1/tenants", ADMIN, PROPERTY_MANAGER),
        Rule.of("GET", "/api/v1/leases/" + ZERO, ADMIN, PROPERTY_MANAGER, ACCOUNTANT),
        Rule.of("POST", "/api/v1/leases", ADMIN, PROPERTY_MANAGER),
        Rule.of("GET", "/api/v1/invoices", ADMIN, PROPERTY_MANAGER, ACCOUNTANT),
        Rule.of("POST", "/api/v1/invoices/generate", ADMIN, ACCOUNTANT),
        Rule.of("POST", "/api/v1/meter-readings", ADMIN, PROPERTY_MANAGER, CARETAKER),
        Rule.of("POST", "/api/v1/payments/manual", ADMIN, ACCOUNTANT),
        Rule.of("POST", "/api/v1/payments/mpesa/stk-push", ADMIN, ACCOUNTANT),
        Rule.of("GET", "/api/v1/complaints", ADMIN, PROPERTY_MANAGER, CARETAKER),
        Rule.of("PATCH", "/api/v1/complaints/" + ZERO + "/assign", ADMIN, PROPERTY_MANAGER),
        Rule.of("GET", "/api/v1/reports/summary", ADMIN, PROPERTY_MANAGER, ACCOUNTANT),
        Rule.of("POST", "/api/v1/billing/run-overdue-sweep", ADMIN).denialOnly(),
        Rule.of("GET", "/api/v1/audit", ADMIN),
        Rule.of("GET", "/api/v1/notifications", ADMIN),
        Rule.of("POST", "/api/v1/auth/register", ADMIN),
        Rule.of("GET", "/api/v1/me/invoices", TENANT),
        Rule.of("GET", "/api/v1/me/complaints", TENANT)
    );

    @Test
    void everyEndpointIsOpenOnlyToTheRolesThatShouldUseIt() {
        Map<Role, String> tokens = new EnumMap<>(Role.class);
        for (Role role : List.of(ADMIN, PROPERTY_MANAGER, ACCOUNTANT, CARETAKER)) {
            tokens.put(role, staff(role).token());
        }
        tokens.put(TENANT, tenantWithInvoice("10000").token());

        List<String> failures = new ArrayList<>();

        for (Rule rule : RULES) {
            Response anonymous = api().send(rule.method(), rule.path(), null, null);
            if (anonymous.status() != 403) {
                failures.add("anonymous " + rule.method() + " " + rule.path()
                    + " returned " + anonymous.status() + " (expected 403)");
            }

            for (Map.Entry<Role, String> caller : tokens.entrySet()) {
                boolean allowed = rule.allowed().contains(caller.getKey());
                if (allowed && !rule.checkAllowed()) {
                    continue;
                }

                int status = api().send(rule.method(), rule.path(), caller.getValue(), null).status();

                if (!allowed && status != 403) {
                    failures.add(caller.getKey() + " " + rule.method() + " " + rule.path()
                        + " returned " + status + " (expected 403)");
                }
                if (allowed && status == 403) {
                    failures.add(caller.getKey() + " " + rule.method() + " " + rule.path()
                        + " was refused (expected to be allowed)");
                }
            }
        }

        assertThat(failures).as("access control mismatches").isEmpty();
    }

    @Test
    void publicEndpointsStayOpen() {
        assertThat(api().send("GET", "/actuator/health", null, null).status()).isEqualTo(200);

        // Wrong credentials are handled by the application (400), not blocked by security (403).
        Response login = api().send("POST", "/api/v1/auth/login", null,
            "{\"email\":\"nobody@example.com\",\"password\":\"whatever123\"}");
        assertThat(login.status()).isEqualTo(400);

        // The password-reset request is open, and answers the same for any number.
        Response reset = api().send("POST", "/api/v1/auth/password-reset/request", null,
            "{\"phoneNumber\":\"0700000000\"}");
        assertThat(reset.status()).isEqualTo(202);
    }
}