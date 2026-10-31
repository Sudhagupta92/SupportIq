package com.supportiq.service.chat;

import com.supportiq.dto.CreateTicketRequest;
import com.supportiq.dto.TicketResponse;
import com.supportiq.dto.TicketSummaryDto;
import com.supportiq.exception.TicketRateLimitException;
import com.supportiq.model.TicketCategory;
import com.supportiq.model.TicketPriority;
import com.supportiq.model.TicketSource;
import com.supportiq.service.ticket.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool service providing support ticket creation for LLM tool calling.
 * 
 * Strict Security Rules:
 * 1. The tool declaration NEVER exposes userId, customerId, or email parameters to the LLM.
 * 2. Authenticated user ID is supplied strictly by the server-side security context.
 * 3. Any extra user-like arguments from the model are strictly ignored.
 * 4. Priority is computed by the backend EscalationDetector, NOT chosen by the model.
 * 5. Applies 24-hour rate limit and duplicate protection.
 * 6. Logs ONLY metadata (never message text or ticket bodies).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketToolService {

    public static final String TOOL_CREATE_SUPPORT_TICKET = "create_support_ticket";

    private final TicketService ticketService;
    private final EscalationDetector escalationDetector;

    /**
     * Builds the Gemini tool declaration for create_support_ticket.
     * Note: Strictly NO userId parameter in schema.
     */
    public Map<String, Object> getToolDeclaration() {
        Map<String, Object> declaration = new LinkedHashMap<>();
        declaration.put("name", TOOL_CREATE_SUPPORT_TICKET);
        declaration.put("description",
                "Creates a support ticket for the authenticated customer when they explicitly request a ticket or ask to talk to a human agent.");
        declaration.put("parameters", Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "summary", Map.of(
                                "type", "STRING",
                                "description", "Brief summary or subject describing the issue (max 120 characters)."
                        ),
                        "category", Map.of(
                                "type", "STRING",
                                "description", "Category of the issue: ORDER_ISSUE, REFUND, DAMAGED_ITEM, DELIVERY, ACCOUNT, or OTHER."
                        ),
                        "relatedOrderNumber", Map.of(
                                "type", "STRING",
                                "description", "Optional numeric order number (digits only) if the customer inquiry refers to a specific order."
                        )
                ),
                "required", List.of("summary")
        ));

        return declaration;
    }

    /**
     * Executes the create_support_ticket tool securely.
     *
     * @param arguments arguments dictionary supplied by the model
     * @param authenticatedUserId user ID from the security principal
     * @param customerQuery original user query string (used for computing priority safely)
     * @return ToolExecutionResult with response data and created ticket metadata
     */
    public ToolExecutionResult executeTool(
            Map<String, Object> arguments,
            Long authenticatedUserId,
            String customerQuery,
            List<Map<String, Object>> recentToolResponses) {

        if (authenticatedUserId == null) {
            log.warn("Unauthorized ticket creation attempt without authenticated userId");
            return new ToolExecutionResult(Map.of("success", false, "error", "Authentication required"), Collections.emptyList());
        }

        // 1. Extract summary (untrusted model data: clean and enforce max 120 chars)
        Object summaryObj = arguments != null ? arguments.get("summary") : null;
        String summary = summaryObj != null ? String.valueOf(summaryObj).trim() : "";
        if (summary.isBlank()) {
            summary = "Customer support inquiry";
        }
        if (summary.length() > 120) {
            summary = summary.substring(0, 117) + "...";
        }

        // 2. Extract and parse category
        Object categoryObj = arguments != null ? arguments.get("category") : null;
        TicketCategory category = parseCategory(categoryObj != null ? String.valueOf(categoryObj) : null);

        // 3. Extract relatedOrderNumber (optional)
        Object orderNumberObj = arguments != null ? arguments.get("relatedOrderNumber") : null;
        String relatedOrderNumber = orderNumberObj != null ? String.valueOf(orderNumberObj).trim() : null;
        if (relatedOrderNumber != null && relatedOrderNumber.isBlank()) {
            relatedOrderNumber = null;
        }

        // 4. Backend computes priority: NEVER trusted to model
        TicketPriority computedPriority = escalationDetector.determinePriority(
                customerQuery != null ? customerQuery : summary,
                recentToolResponses
        );

        CreateTicketRequest request = CreateTicketRequest.builder()
                .subject(summary)
                .description(customerQuery != null && !customerQuery.isBlank() ? customerQuery.trim() : summary)
                .category(category)
                .priority(computedPriority)
                .relatedOrderNumber(relatedOrderNumber)
                .build();

        try {
            TicketResponse ticketResponse = ticketService.createTicket(
                    authenticatedUserId,
                    request,
                    TicketSource.CHAT_TOOL,
                    "EXPLICIT_CHAT_REQUEST"
            );

            TicketSummaryDto summaryDto = TicketSummaryDto.builder()
                    .ticketNumber(ticketResponse.getTicketNumber())
                    .subject(ticketResponse.getSubject())
                    .existing(ticketResponse.isExisting())
                    .build();

            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("success", true);
            responseData.put("ticketNumber", ticketResponse.getTicketNumber());
            responseData.put("existing", ticketResponse.isExisting());
            responseData.put("status", ticketResponse.getStatus().name());

            if (ticketResponse.isExisting()) {
                responseData.put("message",
                        "Ticket " + ticketResponse.getTicketNumber() + " is already open for this order. We added your note to it.");
            } else {
                responseData.put("message",
                        "Ticket " + ticketResponse.getTicketNumber() + " created successfully.");
            }

            List<String> referencedOrders = ticketResponse.getRelatedOrderNumber() != null
                    ? List.of(ticketResponse.getRelatedOrderNumber())
                    : Collections.emptyList();

            return new ToolExecutionResult(responseData, referencedOrders, summaryDto);

        } catch (TicketRateLimitException rateEx) {
            Map<String, Object> responseData = Map.of(
                    "success", false,
                    "error", rateEx.getMessage()
            );
            return new ToolExecutionResult(responseData, Collections.emptyList());
        } catch (Exception ex) {
            log.error("Failed to execute create_support_ticket tool: {}", ex.getMessage());
            Map<String, Object> responseData = Map.of(
                    "success", false,
                    "error", "Unable to create support ticket at this time. Please try again later."
            );
            return new ToolExecutionResult(responseData, Collections.emptyList());
        }
    }

    private TicketCategory parseCategory(String categoryStr) {
        if (categoryStr == null || categoryStr.isBlank()) {
            return TicketCategory.ORDER_ISSUE;
        }
        String clean = categoryStr.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        try {
            return TicketCategory.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return TicketCategory.OTHER;
        }
    }
}
