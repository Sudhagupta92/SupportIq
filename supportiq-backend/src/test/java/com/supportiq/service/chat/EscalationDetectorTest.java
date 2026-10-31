package com.supportiq.service.chat;

import com.supportiq.dto.EscalationDto;
import com.supportiq.model.TicketPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EscalationDetectorTest {

    private EscalationDetector detector;

    @BeforeEach
    void setUp() {
        detector = new EscalationDetector(
                "fraud,scam,cheated,damaged,broken,dhokha,toota,loot,fake,chor",
                "refund not received,refund,complaint,wrong item,manager,worst,angry,bekar,kharab,wapas nahi mila,not received,defective,gussa"
        );
    }

    @Test
    @DisplayName("HIGH priority escalation on fraud, scam, cheated, damaged, broken (English and Hinglish)")
    void testHighPriorityEscalation() {
        EscalationDto res1 = detector.detect("I was cheated and this website is a fraud!", true, Collections.emptyList());
        assertThat(res1.isSuggested()).isTrue();
        assertThat(res1.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(res1.getReason()).isEqualTo(EscalationDetector.REASON_FRAUD);

        EscalationDto res2 = detector.detect("mera item damaged nikla packet me", true, Collections.emptyList());
        assertThat(res2.isSuggested()).isTrue();
        assertThat(res2.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(res2.getReason()).isEqualTo(EscalationDetector.REASON_DAMAGE);

        EscalationDto res3 = detector.detect("yeh toota hua product bheja hai", true, Collections.emptyList());
        assertThat(res3.isSuggested()).isTrue();
        assertThat(res3.getPriority()).isEqualTo(TicketPriority.HIGH);

        EscalationDto res4 = detector.detect("mere sath dhokha hua hai", true, Collections.emptyList());
        assertThat(res4.isSuggested()).isTrue();
        assertThat(res4.getPriority()).isEqualTo(TicketPriority.HIGH);
    }

    @Test
    @DisplayName("MEDIUM priority escalation on refund, complaint, wrong item, or cancelled/overdue order")
    void testMediumPriorityEscalation() {
        EscalationDto res1 = detector.detect("my refund not received yet", true, Collections.emptyList());
        assertThat(res1.isSuggested()).isTrue();
        assertThat(res1.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(res1.getReason()).isEqualTo(EscalationDetector.REASON_REFUND);

        EscalationDto res2 = detector.detect("worst service, I want to file a complaint to manager", true, Collections.emptyList());
        assertThat(res2.isSuggested()).isTrue();
        assertThat(res2.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(res2.getReason()).isEqualTo(EscalationDetector.REASON_COMPLAINT);

        // Cancelled order in tool result
        Map<String, Object> orderToolResult = Map.of(
                "orderNumber", "1004",
                "status", "CANCELLED"
        );
        EscalationDto res3 = detector.detect("What is the status of my order 1004?", true, List.of(orderToolResult));
        assertThat(res3.isSuggested()).isTrue();
        assertThat(res3.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(res3.getReason()).isEqualTo(EscalationDetector.REASON_ORDER_CANCELLED);

        // Overdue order in tool result
        Map<String, Object> overdueOrder = Map.of(
                "orderNumber", "1005",
                "status", "SHIPPED",
                "expectedDelivery", LocalDateTime.now().minusDays(2).toString()
        );
        EscalationDto res4 = detector.detect("When will package arrive?", true, List.of(overdueOrder));
        assertThat(res4.isSuggested()).isTrue();
        assertThat(res4.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(res4.getReason()).isEqualTo(EscalationDetector.REASON_ORDER_OVERDUE);
    }

    @Test
    @DisplayName("LOW priority escalation when answer is not available (answered=false) without complaint words")
    void testLowPriorityEscalation_UnansweredPolicy() {
        EscalationDto res = detector.detect("Do you have vegan leather jackets in brown?", false, Collections.emptyList());
        assertThat(res.isSuggested()).isTrue();
        assertThat(res.getPriority()).isEqualTo(TicketPriority.LOW);
        assertThat(res.getReason()).isEqualTo(EscalationDetector.REASON_UNANSWERED);
    }

    @Test
    @DisplayName("No escalation suggested when answered and no complaint signals present")
    void testNoEscalationSuggested() {
        EscalationDto res = detector.detect("What is your standard return window?", true, Collections.emptyList());
        assertThat(res.isSuggested()).isFalse();
        assertThat(res.getPriority()).isNull();
        assertThat(res.getReason()).isNull();
    }
}
