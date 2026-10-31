package com.supportiq.service.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExplicitEscalationRequestDetectorTest {

    private ExplicitEscalationRequestDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ExplicitEscalationRequestDetector(ExplicitEscalationRequestDetector.DEFAULT_EXPLICIT_KEYWORDS);
    }

    @Test
    @DisplayName("Recognizes explicit English ticket and human agent escalation phrases")
    void testEnglishExplicitEscalation() {
        assertThat(detector.isExplicitEscalationRequest("Please create a ticket for my damaged item")).isTrue();
        assertThat(detector.isExplicitEscalationRequest("Can you raise a ticket about order 1004?")).isTrue();
        assertThat(detector.isExplicitEscalationRequest("I want to talk to a human right now")).isTrue();
        assertThat(detector.isExplicitEscalationRequest("Connect me to a human agent please")).isTrue();
        assertThat(detector.isExplicitEscalationRequest("I need to speak to a human")).isTrue();
        assertThat(detector.isExplicitEscalationRequest("Please register complaint against the delivery partner")).isTrue();
        assertThat(detector.isExplicitEscalationRequest("I want to file a complaint")).isTrue();
    }

    @Test
    @DisplayName("Recognizes explicit Hinglish escalation phrases in Roman script")
    void testHinglishExplicitEscalation() {
        assertThat(detector.isExplicitEscalationRequest("mera ticket banao order 1004 ka")).isTrue();
        assertThat(detector.isExplicitEscalationRequest("bhai ek ticket bana do")).isTrue();
        assertThat(detector.isExplicitEscalationRequest("kisi insaan se baat kara do")).isTrue();
        assertThat(detector.isExplicitEscalationRequest("support se baat karao jaldi")).isTrue();
        assertThat(detector.isExplicitEscalationRequest("agent se baat karni hai mujhe")).isTrue();
    }

    @Test
    @DisplayName("Returns false for inquiries without explicit escalation intent")
    void testNegativeQueriesWithoutExplicitIntent() {
        assertThat(detector.isExplicitEscalationRequest("What is the return policy for footwear?")).isFalse();
        assertThat(detector.isExplicitEscalationRequest("Mera order 1001 kahan hai?")).isFalse();
        assertThat(detector.isExplicitEscalationRequest("Show my recent orders")).isFalse();
        assertThat(detector.isExplicitEscalationRequest("Do you sell pizza?")).isFalse();
        assertThat(detector.isExplicitEscalationRequest("Can I book a flight ticket?")).isFalse();
        assertThat(detector.isExplicitEscalationRequest("Why is shipping so slow?")).isFalse();
    }
}
