package com.kasa.estate_management.notification.provider;

import com.kasa.estate_management.notification.model.Notification;
import com.kasa.estate_management.notification.model.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "notifications.sms.provider", havingValue = "africastalking")
public class AfricasTalkingSmsProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(AfricasTalkingSmsProvider.class);

    private final RestClient restClient;
    private final String username;
    private final String apiKey;
    private final String senderId;

    public AfricasTalkingSmsProvider(
        @Value("${africastalking.username:sandbox}") String username,
        @Value("${africastalking.api-key:}") String apiKey,
        @Value("${africastalking.sender-id:}") String senderId
    ) {
        // Fail at startup, not on every send, if the provider is chosen without credentials.
        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                "AT_API_KEY must be set when notifications.sms.provider=africastalking");
        }

        this.username = username;
        this.apiKey = apiKey;
        this.senderId = senderId;

        String baseUrl = "sandbox".equals(username)
            ? "https://api.sandbox.africastalking.com"
            : "https://api.africastalking.com";
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(Notification notification) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("to", notification.getRecipient());
        form.add("message", notification.getBody());
        if (!senderId.isBlank()) {
            form.add("from", senderId);
        }

        Map<String, Object> response;
        try {
            response = restClient.post()
                .uri("/version1/messaging")
                .header("apiKey", apiKey)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientResponseException e) {
            throw new NotificationDeliveryException(
                "Africa's Talking rejected the request (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new NotificationDeliveryException(
                "Could not reach Africa's Talking: " + e.getClass().getSimpleName(), e);
        }

        checkResponse(response);
        log.info("SMS accepted by Africa's Talking for notification {}", notification.getId());
    }

    /** HTTP 2xx is not enough: the per-recipient status code says whether the message was accepted. */
    private void checkResponse(Map<String, Object> response) {
        Object data = (response == null) ? null : response.get("SMSMessageData");
        if (!(data instanceof Map<?, ?> dataMap)) {
            throw new NotificationDeliveryException("Unexpected response from Africa's Talking");
        }

        Object recipients = dataMap.get("Recipients");
        if (!(recipients instanceof List<?> list) || list.isEmpty()) {
            throw new NotificationDeliveryException(
                "Africa's Talking accepted no recipients: " + dataMap.get("Message"));
        }

        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> recipient)) {
            throw new NotificationDeliveryException("Unexpected recipient format from Africa's Talking");
        }

        // 100 processed, 101 sent, 102 queued: all mean Africa's Talking has taken the message.
        int code = toInt(recipient.get("statusCode"));
        if (code < 100 || code > 102) {
            throw new NotificationDeliveryException(
                "Africa's Talking status " + recipient.get("status") + " (code " + code + ")");
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}