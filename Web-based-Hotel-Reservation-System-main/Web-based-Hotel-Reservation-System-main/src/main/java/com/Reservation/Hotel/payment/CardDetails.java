package com.Reservation.Hotel.payment;

import java.time.YearMonth;
import java.util.Locale;

/**
 * Card data typed on the checkout page. Lives only for the duration of the request:
 * it is validated, the gateway is called, and only the brand + last four digits are kept.
 */
public class CardDetails {

    private final String holder;
    private final String number;   // digits only
    private final String expiry;   // MM/YY as typed
    private final String cvv;

    public CardDetails(String holder, String number, String expiry, String cvv) {
        this.holder = holder == null ? "" : holder.trim();
        this.number = number == null ? "" : number.replaceAll("[\\s-]", "");
        this.expiry = expiry == null ? "" : expiry.trim();
        this.cvv = cvv == null ? "" : cvv.trim();
    }

    public String getHolder() { return holder; }
    public String getNumber() { return number; }
    public String getCvv() { return cvv; }

    public String last4() {
        return number.length() >= 4 ? number.substring(number.length() - 4) : number;
    }

    /** Which form field a problem belongs to, so the checkout page can highlight it. */
    public record Problem(String field, String message) {}

    /** Visa, Mastercard or American Express from the card number prefix, otherwise null. */
    public String brand() {
        if (number.matches("^4\\d{12}(\\d{3}){0,2}$")) return "Visa";
        if (number.matches("^(5[1-5]\\d{14}|2(22[1-9]|2[3-9]\\d|[3-6]\\d\\d|7[01]\\d|720)\\d{12})$")) return "Mastercard";
        if (number.matches("^3[47]\\d{13}$")) return "Amex";
        return null;
    }

    /** Brand from the leading digits only (used to give a precise length message). */
    static String brandFromPrefix(String digits) {
        if (digits.matches("^4\\d*")) return "Visa";
        if (digits.matches("^(5[1-5]|2(22[1-9]|2[3-9]\\d|[3-6]\\d\\d|7[01]\\d|720))\\d*")) return "Mastercard";
        if (digits.matches("^3[47]\\d*")) return "Amex";
        return null;
    }

    /** Luhn (mod 10) checksum used by all card networks to catch typing mistakes. */
    public boolean passesLuhn() {
        if (!number.matches("^\\d{12,19}$")) return false;
        int sum = 0;
        boolean doubleIt = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int d = number.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** The month the card expires, or null when the MM/YY (or MM/YYYY) text is not valid. */
    public YearMonth expiryMonth() {
        if (!expiry.matches("^(0[1-9]|1[0-2])\\s*/\\s*(\\d{2}|\\d{4})$")) return null;
        String[] parts = expiry.split("/");
        int month = Integer.parseInt(parts[0].trim());
        int year = Integer.parseInt(parts[1].trim());
        if (year < 100) year += 2000;
        return YearMonth.of(year, month);
    }

    /** @return a message for the first problem found, or null when the card details look valid */
    public String validate(YearMonth now) {
        Problem p = problem(now);
        return p == null ? null : p.message();
    }

    /** Field-by-field checks, in the order the fields appear on the page. */
    public Problem problem(YearMonth now) {
        // Name on card
        if (holder.isEmpty()) return new Problem("cardHolder", "Enter the name exactly as it appears on the card.");
        if (holder.matches(".*\\d.*")) return new Problem("cardHolder", "The name on the card cannot contain numbers.");
        if (!holder.matches("^[\\p{L} .'-]{2,60}$") || !holder.matches(".*\\p{L}.*\\p{L}.*"))
            return new Problem("cardHolder", "Enter the name exactly as it appears on the card.");
        if (holder.contains("  ")) return new Problem("cardHolder", "Remove the extra spaces from the name on the card.");

        // Card number
        if (number.isEmpty()) return new Problem("cardNumber", "Enter your card number.");
        if (!number.matches("^\\d+$")) return new Problem("cardNumber", "The card number can only contain digits.");
        String prefixBrand = brandFromPrefix(number);
        if (prefixBrand == null) return new Problem("cardNumber", "We accept Visa, Mastercard and American Express.");
        if (number.chars().distinct().count() == 1) return new Problem("cardNumber", "That card number is not valid - please check it.");
        String lengthRule = switch (prefixBrand) {
            case "Amex" -> number.length() == 15 ? null : "American Express card numbers have 15 digits.";
            case "Mastercard" -> number.length() == 16 ? null : "Mastercard numbers have 16 digits.";
            default -> (number.length() == 13 || number.length() == 16 || number.length() == 19) ? null
                    : "Visa card numbers have 16 digits.";
        };
        if (lengthRule != null) return new Problem("cardNumber", lengthRule);
        if (!passesLuhn()) return new Problem("cardNumber", "That card number is not valid - please check it.");
        String brand = brand();
        if (brand == null) return new Problem("cardNumber", "We accept Visa, Mastercard and American Express.");

        // Expiry
        if (expiry.isEmpty()) return new Problem("expiry", "Enter the expiry date as MM/YY.");
        YearMonth exp = expiryMonth();
        if (exp == null) return new Problem("expiry", "Enter the expiry date as MM/YY (month 01-12).");
        if (exp.isBefore(now)) return new Problem("expiry", "This card has expired.");
        if (exp.isAfter(now.plusYears(20))) return new Problem("expiry", "Check the expiry date - it is too far in the future.");

        // Security code
        int cvvLength = "Amex".equals(brand) ? 4 : 3;
        if (!cvv.matches("^\\d{" + cvvLength + "}$"))
            return new Problem("cvv", "Enter the " + cvvLength + "-digit security code (CVV)"
                    + ("Amex".equals(brand) ? " printed on the front of the card." : " on the back of the card."));
        return null;
    }

    @Override
    public String toString() {
        // never print the full number or CVV, even by accident in logs
        return "CardDetails[" + brand() + " ****" + last4() + "]";
    }

    public String holderUpper() {
        return holder.toUpperCase(Locale.ROOT);
    }
}
