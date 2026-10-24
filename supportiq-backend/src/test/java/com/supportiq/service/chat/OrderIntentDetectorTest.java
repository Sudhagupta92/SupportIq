package com.supportiq.service.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderIntentDetectorTest {

    private OrderIntentDetector detector;

    @BeforeEach
    void setUp() {
        detector = new OrderIntentDetector(
                "order,orders,track,tracking,parcel,package,delivery,delivered,shipped,status,kahan,kab aayega,ye order,mera order"
        );
    }

    @ParameterizedTest
    @DisplayName("Should detect order intent for English and Hinglish queries")
    @ValueSource(strings = {
            "Mera order 1001 kahan hai?",
            "Show my recent orders",
            "Where is my package?",
            "kab aayega mera saman?",
            "Can you track parcel BD90283411?",
            "What is the status of my order?",
            "Is my delivery shipped?",
            "ye order deliver kab hoga?",
            "Mera order details batao",
            "order 1002",
            "#1001",
            "1005",
            "Check 1010 please",
            "123456789012"
    })
    void testIsOrderIntent_Positive(String query) {
        assertThat(detector.isOrderIntent(query)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("Should return false for non-order questions like pizza or general policy")
    @ValueSource(strings = {
            "Do you sell pizza?",
            "Can I get a pepperoni pizza with extra cheese?",
            "What is your return policy for damaged items?",
            "How do I reset my account password?",
            "Tell me about SupportIQ store",
            "Who founded this company?",
            "12",
            "",
            "   "
    })
    void testIsOrderIntent_Negative(String query) {
        assertThat(detector.isOrderIntent(query)).isFalse();
    }

    @Test
    @DisplayName("Should return false for null message")
    void testIsOrderIntent_Null() {
        assertThat(detector.isOrderIntent(null)).isFalse();
    }
}
