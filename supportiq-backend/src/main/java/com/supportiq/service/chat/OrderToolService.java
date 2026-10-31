package com.supportiq.service.chat;

import com.supportiq.model.Order;
import com.supportiq.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service providing read-only order tools for LLM tool calling.
 * 
 * Strict Security Rules:
 * 1. The tool declarations NEVER expose userId, email, or customerId parameters to the LLM.
 * 2. Authenticated user ID is supplied strictly by the server-side security context.
 * 3. Any attempt by the model to inject or pass user IDs is strictly ignored.
 * 4. Missing orders and orders belonging to other customers return the IDENTICAL {"found": false} response.
 * 5. Validates orderNumber format (digits only, 1 to 12 characters).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderToolService {

    public static final String TOOL_GET_ORDER_STATUS = "get_order_status";
    public static final String TOOL_LIST_MY_RECENT_ORDERS = "list_my_recent_orders";

    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("^\\d{1,12}$");

    private final OrderRepository orderRepository;

    /**
     * Builds the function declarations following the Google Gemini REST specification.
     * Note: Absolutely no userId, customerId, or email parameters are exposed.
     */
    public List<Map<String, Object>> getFunctionDeclarations() {
        Map<String, Object> getOrderStatusDeclaration = new LinkedHashMap<>();
        getOrderStatusDeclaration.put("name", TOOL_GET_ORDER_STATUS);
        getOrderStatusDeclaration.put("description",
                "Retrieves the status, carrier, tracking details, delivery timeline, and items for a specific order number of the authenticated user.");
        getOrderStatusDeclaration.put("parameters", Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "orderNumber", Map.of(
                                "type", "STRING",
                                "description", "The numeric order identifier (digits only, e.g. 1001)."
                        )
                ),
                "required", List.of("orderNumber")
        ));

        Map<String, Object> listMyRecentOrdersDeclaration = new LinkedHashMap<>();
        listMyRecentOrdersDeclaration.put("name", TOOL_LIST_MY_RECENT_ORDERS);
        listMyRecentOrdersDeclaration.put("description",
                "Lists up to 5 most recent orders placed by the currently logged-in user, including order number, status, date, and items summary.");
        listMyRecentOrdersDeclaration.put("parameters", Map.of(
                "type", "OBJECT",
                "properties", Collections.emptyMap()
        ));

        return List.of(getOrderStatusDeclaration, listMyRecentOrdersDeclaration);
    }

    /**
     * Builds the tool declarations following the Google Gemini REST specification.
     * Note: Absolutely no userId, customerId, or email parameters are exposed.
     */
    public List<Map<String, Object>> getToolDeclarations() {
        return List.of(Map.of(
                "functionDeclarations", getFunctionDeclarations()
        ));
    }

    /**
     * Executes the requested tool safely using the authenticated user's ID.
     *
     * @param functionName name of the tool function
     * @param arguments arguments dictionary supplied by the model
     * @param authenticatedUserId user ID from the security principal
     * @return tool execution result containing data map and referenced order numbers
     */
    @Transactional(readOnly = true)
    public ToolExecutionResult executeTool(String functionName, Map<String, Object> arguments, Long authenticatedUserId) {
        if (authenticatedUserId == null) {
            log.warn("Unauthorized tool execution attempt without authenticated userId");
            return new ToolExecutionResult(Map.of("found", false), Collections.emptyList());
        }

        if (TOOL_GET_ORDER_STATUS.equals(functionName)) {
            return executeGetOrderStatus(arguments, authenticatedUserId);
        } else if (TOOL_LIST_MY_RECENT_ORDERS.equals(functionName)) {
            return executeListMyRecentOrders(authenticatedUserId);
        } else {
            log.warn("Unknown tool function name requested: {}", functionName);
            return new ToolExecutionResult(Map.of("error", "Unknown tool"), Collections.emptyList());
        }
    }

    private ToolExecutionResult executeGetOrderStatus(Map<String, Object> arguments, Long authenticatedUserId) {
        Object orderNumberObj = arguments != null ? arguments.get("orderNumber") : null;
        String orderNumberStr = orderNumberObj != null ? String.valueOf(orderNumberObj).trim() : "";

        // Validate orderNumber (digits only, 1 to 12 characters)
        if (!ORDER_NUMBER_PATTERN.matcher(orderNumberStr).matches()) {
            log.info("Invalid orderNumber format requested: {}", orderNumberStr);
            return new ToolExecutionResult(Map.of("found", false), Collections.emptyList());
        }

        // Query repository for order
        Optional<Order> orderOpt = orderRepository.findByOrderNumber(orderNumberStr);

        // Security requirement: If order does not exist OR belongs to another user,
        // return the SAME generic result {"found": false} so order numbers cannot be probed.
        if (orderOpt.isEmpty() || !orderOpt.get().getUser().getId().equals(authenticatedUserId)) {
            return new ToolExecutionResult(Map.of("found", false), Collections.emptyList());
        }

        Order order = orderOpt.get();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("found", true);
        response.put("orderNumber", order.getOrderNumber());
        response.put("status", order.getStatus().name());
        response.put("totalAmount", order.getTotalAmount());
        response.put("currency", order.getCurrency());
        response.put("placedAt", order.getPlacedAt() != null ? order.getPlacedAt().toString() : null);
        response.put("expectedDelivery", order.getExpectedDelivery() != null ? order.getExpectedDelivery().toString() : null);
        response.put("deliveredAt", order.getDeliveredAt() != null ? order.getDeliveredAt().toString() : null);
        response.put("carrier", order.getCarrier());
        response.put("trackingNumber", order.getTrackingNumber());
        response.put("lastUpdateNote", order.getLastUpdateNote());

        List<Map<String, Object>> itemsList = order.getItems().stream().map(item -> {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("itemName", item.getItemName());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("unitPrice", item.getUnitPrice());
            return itemMap;
        }).toList();
        response.put("items", itemsList);

        return new ToolExecutionResult(response, List.of(order.getOrderNumber()));
    }

    private ToolExecutionResult executeListMyRecentOrders(Long authenticatedUserId) {
        List<Order> orders = orderRepository.findByUserIdOrderByPlacedAtDesc(
                authenticatedUserId,
                PageRequest.of(0, 5)
        );

        List<Map<String, Object>> orderSummaries = new ArrayList<>();
        List<String> orderNumbers = new ArrayList<>();
        for (Order o : orders) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNumber", o.getOrderNumber());
            m.put("status", o.getStatus().name());
            m.put("placedAt", o.getPlacedAt() != null ? o.getPlacedAt().toString() : null);
            m.put("totalAmount", o.getTotalAmount());
            m.put("currency", o.getCurrency());

            String itemsSummary = o.getItems().stream()
                    .map(i -> i.getItemName() + " (x" + i.getQuantity() + ")")
                    .collect(Collectors.joining(", "));
            m.put("shortItemSummary", itemsSummary);
            orderSummaries.add(m);
            orderNumbers.add(o.getOrderNumber());
        }

        Map<String, Object> response = Map.of("orders", orderSummaries);
        return new ToolExecutionResult(response, orderNumbers);
    }
}
