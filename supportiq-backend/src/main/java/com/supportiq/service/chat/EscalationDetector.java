package com.supportiq.service.chat;

import com.supportiq.dto.EscalationDto;
import com.supportiq.model.TicketPriority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Non-LLM rule detector for identifying customer dissatisfaction, order problems,
 * and escalation signals.
 * 
 * Strict Rule: Soft escalation signals NEVER create tickets by themselves.
 */
@Slf4j
@Component
public class EscalationDetector {

    public static final String REASON_FRAUD = "FRAUD_SUSPECTED";
    public static final String REASON_DAMAGE = "DAMAGE_REPORTED";
    public static final String REASON_REFUND = "REFUND_REQUESTED";
    public static final String REASON_COMPLAINT = "CUSTOMER_COMPLAINT";
    public static final String REASON_ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String REASON_ORDER_OVERDUE = "ORDER_OVERDUE";
    public static final String REASON_UNANSWERED = "UNANSWERED_POLICY";

    private final List<String> highPriorityKeywords;
    private final List<String> mediumPriorityKeywords;

    public EscalationDetector(
            @Value("${app.escalation.high-keywords:fraud,scam,cheated,damaged,broken,dhokha,toota,loot,fake,chor}") String highKeywords,
            @Value("${app.escalation.medium-keywords:refund not received,refund,complaint,wrong item,manager,worst,angry,bekar,kharab,wapas nahi mila,not received,defective,gussa}") String medKeywords) {

        this.highPriorityKeywords = parseKeywords(highKeywords);
        this.mediumPriorityKeywords = parseKeywords(medKeywords);
    }

    private List<String> parseKeywords(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Evaluates message, answer status, and any tool order results to compute soft escalation signals.
     *
     * @param userMessage customer inquiry text
     * @param answered whether policy/order was answered successfully
     * @param toolResponses list of tool response payloads (e.g. get_order_status output)
     * @return EscalationDto with suggested flag, reason code, and computed priority
     */
    public EscalationDto detect(String userMessage, boolean answered, List<Map<String, Object>> toolResponses) {
        String normalizedMsg = userMessage != null ? userMessage.toLowerCase() : "";

        // 1. Check HIGH priority keywords (fraud, scam, cheated, damaged, broken, etc.)
        for (String kw : highPriorityKeywords) {
            if (containsKeyword(normalizedMsg, kw)) {
                String reason = (kw.contains("damage") || kw.contains("broke") || kw.contains("toot"))
                        ? REASON_DAMAGE
                        : REASON_FRAUD;

                return EscalationDto.builder()
                        .suggested(true)
                        .reason(reason)
                        .priority(TicketPriority.HIGH)
                        .build();
            }
        }

        // 2. Check tool results for cancelled or overdue orders
        if (toolResponses != null) {
            for (Map<String, Object> resp : toolResponses) {
                if (resp != null) {
                    Object statusObj = resp.get("status");
                    if (statusObj != null && "CANCELLED".equalsIgnoreCase(String.valueOf(statusObj))) {
                        return EscalationDto.builder()
                                .suggested(true)
                                .reason(REASON_ORDER_CANCELLED)
                                .priority(TicketPriority.MEDIUM)
                                .build();
                    }

                    // Check overdue delivery
                    Object expectedDeliveryObj = resp.get("expectedDelivery");
                    if (expectedDeliveryObj != null && statusObj != null && !"DELIVERED".equalsIgnoreCase(String.valueOf(statusObj))) {
                        try {
                            LocalDateTime expDate = LocalDateTime.parse(String.valueOf(expectedDeliveryObj));
                            if (expDate.isBefore(LocalDateTime.now())) {
                                return EscalationDto.builder()
                                        .suggested(true)
                                        .reason(REASON_ORDER_OVERDUE)
                                        .priority(TicketPriority.MEDIUM)
                                        .build();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        // 3. Check MEDIUM priority keywords (refund, complaint, wrong item, manager, etc.)
        for (String kw : mediumPriorityKeywords) {
            if (containsKeyword(normalizedMsg, kw)) {
                String reason = kw.contains("refund") || kw.contains("wapas")
                        ? REASON_REFUND
                        : REASON_COMPLAINT;

                return EscalationDto.builder()
                        .suggested(true)
                        .reason(reason)
                        .priority(TicketPriority.MEDIUM)
                        .build();
            }
        }

        // 4. Check LOW priority for "not available" (answered == false)
        if (!answered) {
            return EscalationDto.builder()
                    .suggested(true)
                    .reason(REASON_UNANSWERED)
                    .priority(TicketPriority.LOW)
                    .build();
        }

        // No escalation signal
        return EscalationDto.notSuggested();
    }

    /**
     * Determines the priority for a created ticket given user message and context.
     */
    public TicketPriority determinePriority(String userMessage, List<Map<String, Object>> toolResponses) {
        EscalationDto dto = detect(userMessage, true, toolResponses);
        if (dto.isSuggested() && dto.getPriority() != null) {
            return dto.getPriority();
        }
        return TicketPriority.MEDIUM;
    }

    private boolean containsKeyword(String text, String keyword) {
        if (keyword.contains(" ")) {
            return text.contains(keyword);
        }
        Pattern p = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b", Pattern.CASE_INSENSITIVE);
        return p.matcher(text).find();
    }
}
