package com.rehome.rehome.component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.ReceiptClauseTemplate;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ReceiptClauseTemplateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReceiptClauseRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private static final Set<String> ALLOWED_PLACEHOLDERS = Set.of(
            "floorPrice", "withdrawalFee", "freeWithdrawalWindowDays", "degradedCompensationRate");

    private final ReceiptClauseTemplateRepository receiptClauseTemplateRepository;

    public void validatePlaceholders(String bodyTemplate) {
        Matcher matcher = PLACEHOLDER.matcher(bodyTemplate == null ? "" : bodyTemplate);
        while (matcher.find()) {
            if (!ALLOWED_PLACEHOLDERS.contains(matcher.group(1))) {
                throw new AppException(ErrorCode.RECEIPT_CLAUSE_TEMPLATE_INVALID);
            }
        }
    }

    public ArrayNode render(ConsignmentReceipt receipt) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (ReceiptClauseTemplate template : receiptClauseTemplateRepository.findAllByActiveTrueOrderByDisplayOrderAsc()) {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("displayOrder", template.getDisplayOrder());
            node.put("title", template.getTitle());
            node.put("body", interpolate(template.getBodyTemplate(), receipt));
            array.add(node);
        }
        return array;
    }

    private String interpolate(String template, ConsignmentReceipt receipt) {
        Map<String, String> values = Map.of(
                "floorPrice", formatVnd(receipt.getFloorPrice()),
                "withdrawalFee", formatVnd(receipt.getWithdrawalFee()),
                "freeWithdrawalWindowDays", String.valueOf(receipt.getFreeWithdrawalWindowDays()),
                "degradedCompensationRate", formatRate(receipt.getDegradedCompensationRate()));

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = values.getOrDefault(matcher.group(1), matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String formatVnd(BigDecimal amount) {
        if (amount == null) return "";
        String digits = amount.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString();
        StringBuilder grouped = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            grouped.append(digits.charAt(i));
            count++;
            if (count % 3 == 0 && i != 0) grouped.append('.');
        }
        return grouped.reverse() + " đ";
    }

    private static String formatRate(BigDecimal rate) {
        if (rate == null) return "";
        int percent = rate.multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).intValue();
        return String.format(Locale.ROOT, "%d%%", percent);
    }
}
