package com.kasa.estate_management.common;

public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /**
     * Converts 0712345678, 712345678, +254712345678 or 254712345678 to 254712345678.
     * Returns null if the input is not a Kenyan mobile number (07xx or 01xx).
     */
    public static String normalizeKenyan(String raw) {
        if (raw == null) {
            return null;
        }

        String digits = raw.replaceAll("[^0-9]", "");
        String national;

        if (digits.length() == 10 && digits.startsWith("0")) {
            national = digits.substring(1);
        } else if (digits.length() == 12 && digits.startsWith("254")) {
            national = digits.substring(3);
        } else if (digits.length() == 9) {
            national = digits;
        } else {
            return null;
        }

        if (!(national.startsWith("7") || national.startsWith("1"))) {
            return null;
        }
        return "254" + national;
    }
}