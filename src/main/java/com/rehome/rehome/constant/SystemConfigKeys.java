package com.rehome.rehome.constant;

import java.math.BigDecimal;

public final class SystemConfigKeys {

    private SystemConfigKeys() {}

    public static final String TERM_LENGTH_DAYS = "term_length_days";
    public static final int DEFAULT_TERM_LENGTH_DAYS = 30;

    public static final String MAX_RENEWALS = "max_renewals";
    public static final int DEFAULT_MAX_RENEWALS = 2;

    public static final String FREE_WITHDRAWAL_WINDOW_DAYS = "free_withdrawal_window_days";
    public static final int DEFAULT_FREE_WITHDRAWAL_WINDOW_DAYS = 7;

    public static final String COMMISSION_RATE_TERM1 = "commission_rate_term1";
    public static final BigDecimal DEFAULT_COMMISSION_RATE_TERM1 = new BigDecimal("0.10");

    public static final String COMMISSION_RATE_TERM2 = "commission_rate_term2";
    public static final BigDecimal DEFAULT_COMMISSION_RATE_TERM2 = new BigDecimal("0.15");

    public static final String COMMISSION_RATE_TERM3 = "commission_rate_term3";
    public static final BigDecimal DEFAULT_COMMISSION_RATE_TERM3 = new BigDecimal("0.20");

    public static final String DEGRADED_COMPENSATION_RATE = "degraded_compensation_rate";
    public static final BigDecimal DEFAULT_DEGRADED_COMPENSATION_RATE = new BigDecimal("0.50");

    public static final String FLOOR_PRICE_RATIO = "floor_price_ratio";
    public static final BigDecimal DEFAULT_FLOOR_PRICE_RATIO = new BigDecimal("0.50");

    public static final String WITHDRAWAL_FEE = "withdrawal_fee";
    public static final BigDecimal DEFAULT_WITHDRAWAL_FEE = BigDecimal.ZERO;

    public static final String MAX_CONCURRENT_CAMPAIGNS = "max_concurrent_campaigns";
    public static final int DEFAULT_MAX_CONCURRENT_CAMPAIGNS = 3;

    public static final String LEGAL_IMAGE_RETENTION_DAYS = "legal_image_retention_days";
    public static final int DEFAULT_LEGAL_IMAGE_RETENTION_DAYS = 30;

    public static final String OVERDUE_TASK_HOURS = "overdue_task_hours";
    public static final int DEFAULT_OVERDUE_TASK_HOURS = 48;

    public static final String MIN_PAYOUT_AMOUNT = "min_payout_amount";
    public static final BigDecimal DEFAULT_MIN_PAYOUT_AMOUNT = new BigDecimal("50000");

    public static final String POINTS_VND_PER_POINT = "points_vnd_per_point";
    public static final int DEFAULT_POINTS_VND_PER_POINT = 10000;

    public static final String POINTS_COST_PER_AI_TRYON = "points_cost_per_ai_tryon";
    public static final int DEFAULT_POINTS_COST_PER_AI_TRYON = 10;

    public static final String POINTS_COST_PER_LIVE_TRYON = "points_cost_per_live_tryon";
    public static final int DEFAULT_POINTS_COST_PER_LIVE_TRYON = 20;

    public static final String MAX_PAYOUT_REQUESTS_PER_DAY = "max_payout_requests_per_day";
    public static final int DEFAULT_MAX_PAYOUT_REQUESTS_PER_DAY = 3;

    public static final String END_OF_TERM_DECISION_DAYS = "end_of_term_decision_days";
    public static final int DEFAULT_END_OF_TERM_DECISION_DAYS = 30;

    public static final String DEGRADED_ITEM_DECISION_DAYS = "degraded_item_decision_days";
    public static final int DEFAULT_DEGRADED_ITEM_DECISION_DAYS = 30;

    public static final String BRING_IN_DEADLINE_DAYS = "bring_in_deadline_days";
    public static final int DEFAULT_BRING_IN_DEADLINE_DAYS = 7;

    public static final String RE_CONSIGN_CONFIRM_DAYS = "re_consign_confirm_days";
    public static final int DEFAULT_RE_CONSIGN_CONFIRM_DAYS = 7;

    public static final String RE_CONSIGN_MAX_COUNTER_ROUNDS = "re_consign_max_counter_rounds";
    public static final int DEFAULT_RE_CONSIGN_MAX_COUNTER_ROUNDS = 2;

    public static final String RE_CONSIGN_MODERATOR_RESPONSE_DAYS = "re_consign_moderator_response_days";
    public static final int DEFAULT_RE_CONSIGN_MODERATOR_RESPONSE_DAYS = 2;

    public static final String MAX_PENDING_FORMS_PER_MEMBER = "max_pending_forms_per_member";
    public static final int DEFAULT_MAX_PENDING_FORMS_PER_MEMBER = 5;

    public static final String MAX_PRICE_CHANGES_PER_DAY = "max_price_changes_per_day";
    public static final int DEFAULT_MAX_PRICE_CHANGES_PER_DAY = 1;

    public static final String ORDER_PAYMENT_TIMEOUT_MINUTES = "order_payment_timeout_minutes";
    public static final int DEFAULT_ORDER_PAYMENT_TIMEOUT_MINUTES = 15;

    public static final String ORDER_AUTO_COMPLETE_DAYS = "order_auto_complete_days";
    public static final int DEFAULT_ORDER_AUTO_COMPLETE_DAYS = 3;

    public static final String RETURN_REQUEST_WINDOW_DAYS = "return_request_window_days";
    public static final int DEFAULT_RETURN_REQUEST_WINDOW_DAYS = 3;

    public static final String RETURN_HANDOVER_CARRIER_DAYS = "return_handover_carrier_days";
    public static final int DEFAULT_RETURN_HANDOVER_CARRIER_DAYS = 3;

    public static final String COUNTER_PICKUP_DAYS = "counter_pickup_days";
    public static final int DEFAULT_COUNTER_PICKUP_DAYS = 7;

    public static final String RETURN_SHIP_BACK_FEE_DEADLINE_MINUTES = "return_ship_back_fee_deadline_minutes";
    public static final int DEFAULT_RETURN_SHIP_BACK_FEE_DEADLINE_MINUTES = 30;

    public static final String RETURN_SHIP_BACK_SHIP_WITHIN_DAYS = "return_ship_back_ship_within_days";
    public static final int DEFAULT_RETURN_SHIP_BACK_SHIP_WITHIN_DAYS = 2;

    public static final String MEMBERSHIP_EXPIRY_REMINDER_DAYS = "membership_expiry_reminder_days";
    public static final int DEFAULT_MEMBERSHIP_EXPIRY_REMINDER_DAYS = 3;

    public static final String VOUCHER_MAX_RATIO_OF_COMMISSION = "voucher_max_ratio_of_commission";
    public static final BigDecimal DEFAULT_VOUCHER_MAX_RATIO_OF_COMMISSION = new BigDecimal("1.00");
}