package com.rehome.rehome.initializer;

import com.rehome.rehome.entity.enums.ViolationWordAction;
import com.rehome.rehome.entity.enums.ViolationWordCategory;
import com.rehome.rehome.entity.enums.ViolationWordSeverity;
import com.rehome.rehome.entity.marketplace.ViolationWord;
import com.rehome.rehome.repository.ViolationWordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.rehome.rehome.entity.enums.ViolationWordAction.*;
import static com.rehome.rehome.entity.enums.ViolationWordCategory.*;
import static com.rehome.rehome.entity.enums.ViolationWordSeverity.*;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.initializer.seed-enabled", havingValue = "true")
public class ViolationWordInitializer implements ApplicationRunner {

    private static final List<RuleGroup> SEED_RULES = List.of(
            new RuleGroup(PROFANITY, BLOCKED, AUTO_REJECT, List.of(
                    "địt mẹ mày",
                    "đụ má mày",
                    "đéo mẹ mày",
                    "óc chó",
                    "đồ mất dạy",
                    "đồ khốn nạn",
                    "đồ chó chết",
                    "đồ súc sinh",
                    "đồ vô học",
                    "đồ hạ đẳng",
                    "đồ đần độn",
                    "đồ ngu xuẩn",
                    "đồ rác rưởi",
                    "đồ thối tha",
                    "đồ bẩn thỉu",
                    "đồ đê tiện",
                    "đồ cặn bã",
                    "cút mẹ đi",
                    "cút con mẹ mày",
                    "ngu như chó",
                    "ngu như bò",
                    "ngu hết phần thiên hạ",
                    "địt con mẹ mày",
                    "đụ con mẹ mày",
                    "địt cha mày",
                    "đụ cha mày",
                    "thằng chó chết",
                    "con chó chết",
                    "đồ óc chó",
                    "ngu như lợn",
                    "đồ mặt dày vô liêm sỉ",
                    "đồ khốn kiếp")),
            new RuleGroup(PROFANITY, WARNING, AUTO_FLAG, List.of(
                    "đồ ngu",
                    "đồ điên",
                    "đồ ngốc",
                    "mặt dày",
                    "vô duyên",
                    "ngu hết thuốc chữa",
                    "đồ hỗn láo",
                    "đồ hỗn xược",
                    "đồ láo toét",
                    "thằng mất dạy",
                    "con mất dạy",
                    "đồ ăn hại",
                    "đồ vô tích sự")),
            new RuleGroup(FRAUD, WARNING, NOTIFY_ADMIN, List.of(
                    "chuyển tiền ngoài app",
                    "gửi mình mã otp",
                    "nhập thông tin thẻ để nhận tiền",
                    "cho mình xin mã otp",
                    "đọc mã otp cho mình",
                    "gửi mật khẩu cho mình",
                    "cho mình xin mã pin",
                    "gửi mã cvv cho mình",
                    "nhập mật khẩu để nhận tiền",
                    "đóng phí để nhận thưởng",
                    "chuyển phí xác minh tài khoản",
                    "chuyển cọc ngoài app",
                    "chuyển khoản riêng để giữ hàng"))
    );

    private final ViolationWordRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> existingWords = repository.findAll().stream()
                .map(ViolationWord::getWord)
                .collect(Collectors.toSet());

        List<ViolationWord> missingWords = SEED_RULES.stream()
                .flatMap(group -> group.words().stream()
                        .filter(existingWords::add)
                        .map(word -> ViolationWord.builder()
                                .word(word)
                                .category(group.category())
                                .severity(group.severity())
                                .action(group.action())
                                .build()))
                .toList();

        if (!missingWords.isEmpty()) {
            repository.saveAll(missingWords);
        }
    }

    private record RuleGroup(ViolationWordCategory category, ViolationWordSeverity severity,
                             ViolationWordAction action, List<String> words) {
    }
}
