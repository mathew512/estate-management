package com.kasa.estate_management.security;

import com.kasa.estate_management.audit.model.AuditAction;
import com.kasa.estate_management.audit.service.AuditService;
import com.kasa.estate_management.common.TooManyRequestsException;
import com.kasa.estate_management.security.SlidingWindowLimiter.Decision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class RateLimitService {

    private static final int MAX_KEYS = 10_000;

    private final SlidingWindowLimiter loginByIp;
    private final SlidingWindowLimiter loginByAccount;
    private final SlidingWindowLimiter resetByIp;
    private final AuditService auditService;

    public RateLimitService(
        AuditService auditService,
        @Value("${ratelimit.window-minutes:15}") int windowMinutes,
        @Value("${ratelimit.login.max-attempts-per-ip:20}") int loginPerIp,
        @Value("${ratelimit.login.max-attempts-per-account:5}") int loginPerAccount,
        @Value("${ratelimit.reset.max-requests-per-ip:10}") int resetPerIp
    ) {
        this.auditService = auditService;
        Duration window = Duration.ofMinutes(Math.max(1, windowMinutes));
        this.loginByIp = new SlidingWindowLimiter(loginPerIp, window, MAX_KEYS);
        this.loginByAccount = new SlidingWindowLimiter(loginPerAccount, window, MAX_KEYS);
        this.resetByIp = new SlidingWindowLimiter(resetPerIp, window, MAX_KEYS);
    }

    /**
     * Call before checking the password. Takes one slot from the address and the account; if
     * either is full, throws, and the password is never checked.
     */
    public void beforeLogin(String ip, String email) {
        Instant now = Instant.now();
        String ipKey = ipKey(ip);
        String accountKey = normalize(email);

        Decision byIp = loginByIp.tryAcquire(ipKey, now);
        if (!byIp.allowed()) {
            if (byIp.firstBlock()) {
                auditService.recordBestEffortAs("ip:" + ipKey, null, null, AuditAction.LOGIN_BLOCKED,
                    "USER", null, "Too many login attempts from address " + ipKey
                        + "; blocked for about " + minutes(byIp.retryAfterSeconds()) + " minute(s)");
            }
            throw new TooManyRequestsException(byIp.retryAfterSeconds());
        }

        Decision byAccount = loginByAccount.tryAcquire(accountKey, now);
        if (!byAccount.allowed()) {
            loginByIp.releaseLast(ipKey); // a refused request must not use up the address's allowance
            if (byAccount.firstBlock()) {
                auditService.recordBestEffortAs(accountKey, null, null, AuditAction.LOGIN_BLOCKED,
                    "USER", null, "Too many failed logins for this account; blocked for about "
                        + minutes(byAccount.retryAfterSeconds()) + " minute(s)");
            }
            throw new TooManyRequestsException(byAccount.retryAfterSeconds());
        }
    }

    /** A successful login does not count against the address, and clears the account's counter. */
    public void afterLoginSuccess(String ip, String email) {
        loginByIp.releaseLast(ipKey(ip));
        loginByAccount.clear(normalize(email));
    }

    /** Counts one password-reset request from this address, or throws if it has used its allowance. */
    public void checkReset(String ip) {
        Decision decision = resetByIp.tryAcquire(ipKey(ip), Instant.now());
        if (!decision.allowed()) {
            throw new TooManyRequestsException(decision.retryAfterSeconds());
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        Instant now = Instant.now();
        loginByIp.cleanup(now);
        loginByAccount.cleanup(now);
        resetByIp.cleanup(now);
    }

    private static String ipKey(String ip) {
        return (ip == null || ip.isBlank()) ? "unknown" : ip;
    }

    private static String normalize(String email) {
        String value = (email == null) ? "" : email.trim().toLowerCase(Locale.ROOT);
        return value.length() > 254 ? value.substring(0, 254) : value;
    }

    private static long minutes(long seconds) {
        return Math.max(1, (seconds + 59) / 60);
    }
}