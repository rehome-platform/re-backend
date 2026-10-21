package com.rehome.rehome.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "rehome.consignment")
@Getter
@Setter
public class ConsignmentProperties {
    private int bringInDeadlineDays = 7;
    private int termLengthDays = 30;
    private int maxRenewals = 2;
    private int freeWithdrawalWindowDays = 7;
    private BigDecimal commissionRateTerm1 = new BigDecimal("0.10");
    private BigDecimal commissionRateTerm2 = new BigDecimal("0.15");
    private BigDecimal commissionRateTerm3 = new BigDecimal("0.20");
    private BigDecimal degradedCompensationRate = new BigDecimal("0.50");
    private BigDecimal floorPriceRate = new BigDecimal("0.50");
    private BigDecimal withdrawalFee = new BigDecimal("20000"); // SỐ NÀY MÌNH ĐOÁN, xem lưu ý
}
