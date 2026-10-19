package com.kasa.estate_management.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class MpesaCallbackDtos {

    public record Callback(@JsonProperty("Body") Body body) {}

    public record Body(@JsonProperty("stkCallback") StkCallback stkCallback) {}

    public record StkCallback(
        @JsonProperty("MerchantRequestID") String merchantRequestId,
        @JsonProperty("CheckoutRequestID") String checkoutRequestId,
        @JsonProperty("ResultCode") Integer resultCode,
        @JsonProperty("ResultDesc") String resultDesc,
        @JsonProperty("CallbackMetadata") Metadata callbackMetadata
    ) {}

    public record Metadata(@JsonProperty("Item") List<Item> items) {}

    public record Item(
        @JsonProperty("Name") String name,
        @JsonProperty("Value") Object value
    ) {}
}