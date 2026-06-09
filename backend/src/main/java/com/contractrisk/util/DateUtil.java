package com.contractrisk.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateUtil {

    private static final List<DateTimeFormatter> FORMATTERS = new ArrayList<>();

    static {
        FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy年M月d日"));
        FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy/M/d"));
        FORMATTERS.add(DateTimeFormatter.ofPattern("yyyyMMdd"));
        FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    }

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{4})[年\\-/.](\\d{1,2})[月\\-/.](\\d{1,2})[日号]?|" +
            "(\\d{4})(\\d{2})(\\d{2})"
    );

    public static LocalDateTime parseDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(text.trim(), formatter);
                return date.atStartOfDay();
            } catch (DateTimeParseException ignored) {
            }
        }

        Matcher matcher = DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                int year, month, day;
                if (matcher.group(1) != null) {
                    year = Integer.parseInt(matcher.group(1));
                    month = Integer.parseInt(matcher.group(2));
                    day = Integer.parseInt(matcher.group(3));
                } else {
                    year = Integer.parseInt(matcher.group(4));
                    month = Integer.parseInt(matcher.group(5));
                    day = Integer.parseInt(matcher.group(6));
                }
                return LocalDate.of(year, month, day).atStartOfDay();
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    public static LocalDateTime extractDate(String text, String keyword) {
        if (text == null || keyword == null) {
            return null;
        }

        Pattern pattern = Pattern.compile(
                keyword + "[^\\d]{0,5}(\\d{4}[年\\-/.]\\d{1,2}[月\\-/.]\\d{1,2}[日号]?|" +
                "\\d{4}\\d{2}\\d{2}|\\d{4}\\.\\d{2}\\.\\d{2})"
        );
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return parseDate(matcher.group(1));
        }

        return null;
    }

    public static List<LocalDateTime> extractAllDates(String text) {
        List<LocalDateTime> dates = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return dates;
        }

        Matcher matcher = DATE_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                LocalDateTime date;
                if (matcher.group(1) != null) {
                    int year = Integer.parseInt(matcher.group(1));
                    int month = Integer.parseInt(matcher.group(2));
                    int day = Integer.parseInt(matcher.group(3));
                    date = LocalDate.of(year, month, day).atStartOfDay();
                } else {
                    int year = Integer.parseInt(matcher.group(4));
                    int month = Integer.parseInt(matcher.group(5));
                    int day = Integer.parseInt(matcher.group(6));
                    date = LocalDate.of(year, month, day).atStartOfDay();
                }
                if (date != null) {
                    dates.add(date);
                }
            } catch (Exception ignored) {
            }
        }

        return dates;
    }

    public static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
    }

    public static boolean isOverdue(LocalDateTime targetDate) {
        if (targetDate == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(targetDate);
    }
}
