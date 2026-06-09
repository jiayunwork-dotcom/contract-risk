package com.contractrisk.util;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AmountUtil {

    private static final Map<Character, Integer> CN_NUM_MAP = new HashMap<>();
    private static final Map<Character, BigDecimal> CN_UNIT_MAP = new HashMap<>();

    static {
        CN_NUM_MAP.put('零', 0);
        CN_NUM_MAP.put('壹', 1);
        CN_NUM_MAP.put('贰', 2);
        CN_NUM_MAP.put('叁', 3);
        CN_NUM_MAP.put('肆', 4);
        CN_NUM_MAP.put('伍', 5);
        CN_NUM_MAP.put('陆', 6);
        CN_NUM_MAP.put('柒', 7);
        CN_NUM_MAP.put('捌', 8);
        CN_NUM_MAP.put('玖', 9);
        CN_NUM_MAP.put('一', 1);
        CN_NUM_MAP.put('二', 2);
        CN_NUM_MAP.put('三', 3);
        CN_NUM_MAP.put('四', 4);
        CN_NUM_MAP.put('五', 5);
        CN_NUM_MAP.put('六', 6);
        CN_NUM_MAP.put('七', 7);
        CN_NUM_MAP.put('八', 8);
        CN_NUM_MAP.put('九', 9);
        CN_NUM_MAP.put('两', 2);

        CN_UNIT_MAP.put('拾', BigDecimal.valueOf(10));
        CN_UNIT_MAP.put('佰', BigDecimal.valueOf(100));
        CN_UNIT_MAP.put('仟', BigDecimal.valueOf(1000));
        CN_UNIT_MAP.put('万', BigDecimal.valueOf(10000));
        CN_UNIT_MAP.put('亿', BigDecimal.valueOf(100000000));
        CN_UNIT_MAP.put('元', BigDecimal.ONE);
        CN_UNIT_MAP.put('圆', BigDecimal.ONE);
        CN_UNIT_MAP.put('角', BigDecimal.valueOf(0.1));
        CN_UNIT_MAP.put('分', BigDecimal.valueOf(0.01));
    }

    private static final Pattern ARABIC_AMOUNT_PATTERN =
            Pattern.compile("(?:人民币|RMB|￥|\\$)?\\s*([\\d,]+(?:\\.\\d+)?)(?:\\s*(?:元|人民币|USD|美元))?");

    private static final Pattern CN_AMOUNT_PATTERN =
            Pattern.compile("([零壹贰叁肆伍陆柒捌玖一二三四五六七八九两拾佰仟万亿元圆角分]+)[元圆]?");

    public static BigDecimal parseAmount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        BigDecimal arabicAmount = parseArabicAmount(text);
        if (arabicAmount != null && arabicAmount.compareTo(BigDecimal.ZERO) > 0) {
            return arabicAmount;
        }

        return parseChineseAmount(text);
    }

    public static BigDecimal parseArabicAmount(String text) {
        Matcher matcher = ARABIC_AMOUNT_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                String amountStr = matcher.group(1).replace(",", "");
                return new BigDecimal(amountStr);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static BigDecimal parseChineseAmount(String text) {
        Matcher matcher = CN_AMOUNT_PATTERN.matcher(text);
        if (matcher.find()) {
            String cnAmount = matcher.group(1);
            try {
                return chineseToNumber(cnAmount);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal chineseToNumber(String chinese) {
        BigDecimal result = BigDecimal.ZERO;
        BigDecimal temp = BigDecimal.ZERO;
        BigDecimal section = BigDecimal.ZERO;
        boolean hasUnit = false;

        for (int i = 0; i < chinese.length(); i++) {
            char c = chinese.charAt(i);

            if (CN_NUM_MAP.containsKey(c)) {
                int num = CN_NUM_MAP.get(c);
                if (num == 0) {
                    hasUnit = false;
                } else {
                    if (i > 0 && CN_NUM_MAP.containsKey(chinese.charAt(i - 1))) {
                        temp = temp.multiply(BigDecimal.TEN).add(BigDecimal.valueOf(num));
                    } else {
                        temp = BigDecimal.valueOf(num);
                    }
                    hasUnit = false;
                }
            } else if (CN_UNIT_MAP.containsKey(c)) {
                BigDecimal unit = CN_UNIT_MAP.get(c);
                hasUnit = true;

                if (unit.compareTo(BigDecimal.valueOf(10000)) >= 0) {
                    section = section.add(temp).multiply(unit);
                    result = result.add(section);
                    section = BigDecimal.ZERO;
                } else if (unit.compareTo(BigDecimal.valueOf(10)) >= 0) {
                    if (temp.compareTo(BigDecimal.ZERO) == 0) {
                        temp = BigDecimal.ONE;
                    }
                    section = section.add(temp.multiply(unit));
                } else {
                    section = section.add(temp.multiply(unit));
                }
                temp = BigDecimal.ZERO;
            }
        }

        if (!hasUnit && temp.compareTo(BigDecimal.ZERO) > 0) {
            section = section.add(temp);
        }

        result = result.add(section);
        return result;
    }

    public static boolean exceedsPercentage(BigDecimal amount, BigDecimal totalAmount, double percentage) {
        if (amount == null || totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        BigDecimal threshold = totalAmount.multiply(BigDecimal.valueOf(percentage / 100.0));
        return amount.compareTo(threshold) > 0;
    }

    public static String extractAmountText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        Matcher arabicMatcher = ARABIC_AMOUNT_PATTERN.matcher(text);
        if (arabicMatcher.find()) {
            return arabicMatcher.group();
        }

        Matcher cnMatcher = CN_AMOUNT_PATTERN.matcher(text);
        if (cnMatcher.find()) {
            return cnMatcher.group();
        }

        return null;
    }
}
