package com.rehome.rehome.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SequenceInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    private static final List<String> SEQUENCES = List.of(
            "member_code_seq",
            "product_sku_seq",
            "receipt_no_seq",
            "consignment_form_no_seq"
    );

    @Override
    public void run(ApplicationArguments args) {
        for (String seq : SEQUENCES) {
            jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS " + seq + " START 1");
        }
    }
}
