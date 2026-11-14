package com.rehome.rehome.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.enums.*;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentModerationService {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private final ViolationWordRepository words;
    private final AccountRepository accounts;
    private final AuditLogRepository audits;
    private final NotificationService notifications;
    private List<Rule> rules = List.of();
    private Instant expiresAt = Instant.EPOCH;

    public record WordsChanged() {}
    private record Rule(Pattern pattern, ViolationWordAction action) {}

    @TransactionalEventListener
    public synchronized void invalidate(WordsChanged event) {
        expiresAt = Instant.EPOCH;
    }

    private synchronized List<Rule> activeRules() {
        if (!Instant.now().isBefore(expiresAt)) {
            rules = words.findAllByIsActiveTrue().stream().map(word ->
                    compile(word.getWord(), word.getAction())).filter(Objects::nonNull).toList();
            expiresAt = Instant.now().plus(Duration.ofSeconds(30));
        }
        return rules;
    }

    private static Rule compile(String term, ViolationWordAction action) {
        String phrase = Arrays.stream(normalize(term).strip().split("(?U)\\s+"))
                .map(part -> WORD.matcher(part).results().map(match -> match.group()).collect(Collectors.joining()))
                .filter(part -> !part.isEmpty())
                .map(part -> part.codePoints().mapToObj(cp -> Pattern.quote(Character.toString(cp)))
                        .collect(Collectors.joining("[.*_-]*")))
                .collect(Collectors.joining("(?U)\\s+"));
        return phrase.isEmpty() ? null : new Rule(
                Pattern.compile("(?<![\\p{L}\\p{N}])" + phrase + "(?![\\p{L}\\p{N}])"), action);
    }

    private static String normalize(String text) {
        return MARKS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT).replace('đ', 'd'),
                Normalizer.Form.NFD)).replaceAll("");
    }

    public void check(UUID actorId, String context, String... texts) {
        recordMatch(actorId, context, null, evaluate(texts));
    }

    public ViolationWordAction evaluate(String... texts) {
        List<String> values = Arrays.stream(texts).filter(Objects::nonNull)
                .filter(value -> !value.isBlank()).map(ContentModerationService::normalize).toList();
        if (values.isEmpty()) {
            return null;
        }
        var matches = activeRules().stream().filter(rule ->
                values.stream().anyMatch(value -> rule.pattern().matcher(value).find())).toList();
        if (matches.stream().anyMatch(rule -> rule.action() == ViolationWordAction.AUTO_REJECT)) {
            throw new AppException(ErrorCode.CONTENT_VIOLATION);
        }
        if (matches.stream().anyMatch(rule -> rule.action() == ViolationWordAction.AUTO_FLAG)) {
            return ViolationWordAction.AUTO_FLAG;
        }
        return matches.isEmpty() ? null : ViolationWordAction.NOTIFY_ADMIN;
    }

    public void recordMatch(UUID actorId, String context, UUID contentId, ViolationWordAction action) {
        if (action == null) {
            return;
        }
        boolean flagged = action == ViolationWordAction.AUTO_FLAG;
        var detail = JsonNodeFactory.instance.objectNode().put("context", context);
        if (contentId != null) {
            detail.put("contentId", contentId.toString());
        }
        // Keep private conversation text out of the shared audit log.
        AuditLog audit = audits.save(AuditLog.builder().actorAccount(accounts.getReferenceById(actorId))
                .actionType(flagged ? "CONTENT_AUTO_FLAG" : "CONTENT_NOTIFY_ADMIN")
                .targetType("CONTENT_REVIEW").targetId(UUID.randomUUID().toString()).detail(detail).build());
        if (!flagged) {
            for (var admin : accounts.findByRole(Role.ADMIN)) {
                notifications.notify(admin, NotificationType.CONTENT_REVIEW, "Cần xem xét nội dung",
                        "Kiểm tra nhật ký quản trị, mã " + audit.getTargetId(), null, null, null);
            }
        }
    }
}
