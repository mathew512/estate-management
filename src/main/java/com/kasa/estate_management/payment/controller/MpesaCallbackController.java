package com.kasa.estate_management.payment.controller;

import com.kasa.estate_management.payment.dto.MpesaCallbackDtos.Callback;
import com.kasa.estate_management.payment.dto.MpesaCallbackDtos.Item;
import com.kasa.estate_management.payment.dto.MpesaCallbackDtos.StkCallback;
import com.kasa.estate_management.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/mpesa")
public class MpesaCallbackController {

    private static final Logger log = LoggerFactory.getLogger(MpesaCallbackController.class);

    private final PaymentService paymentService;
    private final String callbackSecret;

    public MpesaCallbackController(PaymentService paymentService,
                                   @Value("${mpesa.callback-secret:}") String callbackSecret) {
        this.paymentService = paymentService;
        this.callbackSecret = callbackSecret;
    }

    @PostMapping("/callback/{secret}")
    public ResponseEntity<Map<String, Object>> callback(@PathVariable("secret") String secret,
                                                        @RequestBody Callback payload) {
        if (!secretMatches(secret)) {
            log.warn("M-Pesa callback rejected: wrong or unconfigured secret");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (payload == null || payload.body() == null || payload.body().stkCallback() == null) {
            throw new IllegalArgumentException("Malformed M-Pesa callback");
        }

        StkCallback cb = payload.body().stkCallback();
        if (cb.checkoutRequestId() == null || cb.resultCode() == null) {
            throw new IllegalArgumentException("Malformed M-Pesa callback");
        }

        BigDecimal amount = null;
        String receipt = null;

        if (cb.callbackMetadata() != null && cb.callbackMetadata().items() != null) {
            for (Item item : cb.callbackMetadata().items()) {
                if (item.value() == null) {
                    continue;
                }
                if ("Amount".equals(item.name())) {
                    amount = new BigDecimal(String.valueOf(item.value()));
                } else if ("MpesaReceiptNumber".equals(item.name())) {
                    receipt = String.valueOf(item.value());
                }
            }
        }

        paymentService.handleMpesaCallback(
            cb.checkoutRequestId(), cb.resultCode(), cb.resultDesc(), amount, receipt);

        return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
    }

    private boolean secretMatches(String provided) {
        if (callbackSecret.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
            callbackSecret.getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8));
    }
}