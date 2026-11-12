package com.kasa.estate_management.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @Test
    void normalizesEveryCommonKenyanFormat() {
        assertThat(PhoneNumbers.normalizeKenyan("0712345678")).isEqualTo("254712345678");
        assertThat(PhoneNumbers.normalizeKenyan("+254712345678")).isEqualTo("254712345678");
        assertThat(PhoneNumbers.normalizeKenyan("254712345678")).isEqualTo("254712345678");
        assertThat(PhoneNumbers.normalizeKenyan("712345678")).isEqualTo("254712345678");
        assertThat(PhoneNumbers.normalizeKenyan("0112345678")).isEqualTo("254112345678");
        assertThat(PhoneNumbers.normalizeKenyan("07 12 345-678")).isEqualTo("254712345678");
    }

    @Test
    void rejectsAnythingThatIsNotAKenyanMobileNumber() {
        assertThat(PhoneNumbers.normalizeKenyan(null)).isNull();
        assertThat(PhoneNumbers.normalizeKenyan("")).isNull();
        assertThat(PhoneNumbers.normalizeKenyan("12345")).isNull();
        assertThat(PhoneNumbers.normalizeKenyan("0212345678")).isNull();
        assertThat(PhoneNumbers.normalizeKenyan("254212345678")).isNull();
        assertThat(PhoneNumbers.normalizeKenyan("+1 555 123 4567")).isNull();
    }
}