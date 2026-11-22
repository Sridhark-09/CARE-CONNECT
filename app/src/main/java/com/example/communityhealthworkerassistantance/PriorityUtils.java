package com.example.communityhealthworkerassistantance;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class PriorityUtils {

    public static final String HIGH = "HIGH";
    public static final String DUE_TODAY = "DUE_TODAY";
    public static final String UPCOMING = "UPCOMING";
    public static final String UNKNOWN = "UNKNOWN";

    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());

    // Classifies a follow-up date string (dd-MM-yyyy) against today's date
    public static String classify(String followUpDateStr) {
        if (followUpDateStr == null || followUpDateStr.trim().isEmpty()) {
            return UNKNOWN;
        }

        try {
            Date followUpDate = FORMAT.parse(followUpDateStr.trim());
            Date today = stripTime(new Date());
            Date followUpStripped = stripTime(followUpDate);

            if (followUpStripped.before(today)) {
                return HIGH;
            } else if (followUpStripped.equals(today)) {
                return DUE_TODAY;
            } else {
                return UPCOMING;
            }
        } catch (ParseException e) {
            return UNKNOWN;
        }
    }

    private static Date stripTime(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public static String label(String priority) {
        switch (priority) {
            case HIGH: return "High priority";
            case DUE_TODAY: return "Due today";
            case UPCOMING: return "Upcoming";
            default: return "No date set";
        }
    }
}