package com.kasa.estate_management.payment.mpesa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MpesaClient {

    public record StkPushResult(
        String merchantRequestId,
        String checkoutRequestId,
        String responseCode,
        String customerMessage
    ) {}

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private final RestClient restClient;
    private final String consumerKey;
    private final String consumerSecret;
    private final String shortcode;
    private final String passkey;
    private final String callbackUrl;

    private final Object tokenLock = new Object();
    private String cachedToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public MpesaClient(
        @Value("${mpesa.base-url:https://sandbox.safaricom.co.ke}") String baseUrl,
        @Value("${mpesa.consumer-key:}") String consumerKey,
        @Value("${mpesa.consumer-secret:}") String consumerSecret,
        @Value("${mpesa.shortcode:174379}") String shortcode,
        @Value("${mpesa.passkey:}") String passkey,
        @Value("${mpesa.callback-url:}") String callbackUrl
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.shortcode = shortcode;
        this.passkey = passkey;
        this.callbackUrl = callbackUrl;
    }

    public StkPushResult stkPush(String phoneNumber, BigDecimal amount, String accountReference, String description) {
        if (consumerKey.isBlank() || consumerSecret.isBlank() || passkey.isBlank() || callbackUrl.isBlank()) {
            throw new IllegalStateException("M-Pesa is not configured: set the MPESA_* environment variables");
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("M-Pesa amounts must be whole shillings");
        }

        String timestamp = LocalDateTime.now(NAIROBI).format(TIMESTAMP_FORMAT);
        String password = Base64.getEncoder().encodeToString(
            (shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));
        String phone = normalizePhone(phoneNumber);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", shortcode);
        body.put("Password", password);
        body.put("Timestamp", timestamp);
        body.put("TransactionType", "CustomerPayBillOnline");
        body.put("Amount", amount.intValueExact());
        body.put("PartyA", phone);
        body.put("PartyB", shortcode);
        body.put("PhoneNumber", phone);
        body.put("CallBackURL", callbackUrl);
        body.put("AccountReference", accountReference);
        body.put("TransactionDesc", description);

        Map<String, Object> response = restClient.post()
            .uri("/mpesa/stkpush/v1/processrequest")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null) {
            throw new IllegalStateException("Empty response from M-Pesa");
        }

        return new StkPushResult(
            String.valueOf(response.get("MerchantRequestID")),
            String.valueOf(response.get("CheckoutRequestID")),
            String.valueOf(response.get("ResponseCode")),
            String.valueOf(response.get("CustomerMessage"))
        );
    }

    private String getAccessToken() {
        synchronized (tokenLock) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
                return cachedToken;
            }

            String credentials = Base64.getEncoder().encodeToString(
                (consumerKey + ":" + consumerSecret).getBytes(StandardCharsets.UTF_8));

            Map<String, Object> response = restClient.get()
                .uri("/oauth/v1/generate?grant_type=client_credentials")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null || response.get("access_token") == null) {
                throw new IllegalStateException("Could not obtain M-Pesa access token");
            }

            cachedToken = String.valueOf(response.get("access_token"));
            long expiresIn = Long.parseLong(String.valueOf(response.get("expires_in")));
            tokenExpiry = Instant.now().plusSeconds(expiresIn - 60);
            return cachedToken;
        }
    }

    /** Converts 0712345678, +254712345678 or 254712345678 to 254712345678. */
    static String normalizePhone(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("0") && digits.length() == 10) {
            return "254" + digits.substring(1);
        }
        if (digits.startsWith("254") && digits.length() == 12) {
            return digits;
        }
        throw new IllegalArgumentException("Invalid Kenyan phone number: " + raw);
    }
}