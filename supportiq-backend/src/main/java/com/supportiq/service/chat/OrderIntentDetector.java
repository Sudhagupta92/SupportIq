package com.supportiq.service.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Fast, lightweight, non-LLM detector for customer order inquiries.
 * 
 * Rules:
 * 1. Checks if the query contains a numeric sequence of 3 to 12 digits (e.g. order number 1001).
 * 2. Checks if the query contains common English or Hinglish order keywords (e.g. "order", "track", "kahan", "kab aayega").
 * 3. The keyword list is configurable via 'app.rag.order-intent-keywords'.
 */
@Component
public class OrderIntentDetector {

    private static final Pattern DIGIT_PATTERN = Pattern.compile("(?<!\\d)\\d{3,12}(?!\\d)");

    private final List<String> keywords;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderIntentDetector(
            @Value("${app.rag.order-intent-keywords:order,orders,track,tracking,parcel,package,delivery,delivered,shipped,status,kahan,kab aayega,ye order,mera order}")
            String keywordsConfig) {

        if (keywordsConfig != null && !keywordsConfig.isBlank()) {
            this.keywords = Arrays.stream(keywordsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toLowerCase)
                    .toList();
        } else {
            this.keywords = List.of(
                    "order", "orders", "track", "tracking", "parcel", "package",
                    "delivery", "delivered", "shipped", "status", "kahan", "kab aayega", "ye order", "mera order"
            );
        }
    }

    public OrderIntentDetector(List<String> keywords) {
        if (keywords != null) {
            this.keywords = keywords.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toLowerCase)
                    .toList();
        } else {
            this.keywords = Collections.emptyList();
        }
    }

    /**
     * Evaluates whether the incoming query indicates intent regarding orders.
     *
     * @param message customer query string
     * @return true if 3-12 digits or any configured order keywords match
     */
    public boolean isOrderIntent(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        // 1. Check for 3 to 12 digit order numbers
        if (DIGIT_PATTERN.matcher(message).find()) {
            return true;
        }

        // 2. Check for configured keyword/phrase matches (case-insensitive)
        String lowerMessage = message.toLowerCase();
        for (String keyword : keywords) {
            if (lowerMessage.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    public List<String> getKeywords() {
        return Collections.unmodifiableList(keywords);
    }
}
