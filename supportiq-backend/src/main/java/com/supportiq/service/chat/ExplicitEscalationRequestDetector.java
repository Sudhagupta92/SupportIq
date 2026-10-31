package com.supportiq.service.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Non-LLM rule detector for detecting explicit customer requests to raise a ticket
 * or speak with a human support agent.
 * 
 * When this detector fires, the create_support_ticket tool is enabled for the model call.
 * If this detector returns false, create_support_ticket is NOT declared to the model.
 */
@Slf4j
@Component
public class ExplicitEscalationRequestDetector {

    public static final String DEFAULT_EXPLICIT_KEYWORDS =
            "create a ticket,create ticket,raise a ticket,raise ticket,ticket banao,ticket bana do,ticket bana de,ticket kholo,open a ticket,open ticket,talk to a human,talk to human,speak to a human,speak to human,speak with a human,speak with human,human agent,real agent,live agent,insaan se baat,insan se baat,bande se baat,support se baat karao,support se baat karwao,agent se baat,customer care se baat,complaint register,register complaint,file a complaint,file complaint,raise a complaint,raise complaint";

    private final List<String> explicitKeywords;

    public ExplicitEscalationRequestDetector(
            @Value("${app.escalation.explicit-keywords:" + DEFAULT_EXPLICIT_KEYWORDS + "}") String keywords) {

        this.explicitKeywords = parseKeywords(keywords);
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
     * Checks if the user message contains an explicit request to create a ticket or connect with a human.
     *
     * @param message customer query string
     * @return true if an explicit escalation phrase is present; false otherwise
     */
    public boolean isExplicitEscalationRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = message.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();

        for (String kw : explicitKeywords) {
            String normalizedKw = kw.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
            if (containsPhrase(normalized, normalizedKw)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsPhrase(String text, String phrase) {
        if (phrase.isEmpty()) {
            return false;
        }
        Pattern p = Pattern.compile("(?:^|\\s)" + Pattern.quote(phrase) + "(?:$|\\s)", Pattern.CASE_INSENSITIVE);
        return p.matcher(text).find() || text.contains(phrase);
    }
}
