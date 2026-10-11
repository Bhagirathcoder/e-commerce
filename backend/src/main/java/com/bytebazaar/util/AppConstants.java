package com.bytebazaar.util;
import java.math.BigDecimal;
public final class AppConstants {
    private AppConstants() {}
    public static final int RETURN_DAYS = 7;
    public static final BigDecimal TAX_RATE = new BigDecimal("0.05");
    public static final BigDecimal FREE_SHIPPING_ABOVE = new BigDecimal("499");
    public static final BigDecimal SHIPPING_FEE = new BigDecimal("40");
    public static final int UNPAID_UPI_MINUTES = 30;
    public static final String MSG_BLOCKED = "Your ByteBazaar account has been blocked by the administrator. Please contact customer support.";
}
