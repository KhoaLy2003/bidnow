package com.bidnow.media.notification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Value formats shared by notification copy and email variables. */
public final class NotificationFormats {

    private static final DateTimeFormatter DEADLINE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private NotificationFormats() {
    }

    public static String money(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "$" + format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    public static String deadline(Instant instant) {
        return instant == null ? "" : DEADLINE.format(instant);
    }
}
