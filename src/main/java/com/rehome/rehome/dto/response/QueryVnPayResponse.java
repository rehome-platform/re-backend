package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryVnPayResponse {
    // "00" = truy vấn thành công; khác "00" là lỗi ở chính request truy vấn (không phải trạng thái giao dịch)
    @JsonProperty("vnp_ResponseCode")
    private String responseCode;

    @JsonProperty("vnp_Message")
    private String message;

    @JsonProperty("vnp_TmnCode")
    private String tmnCode;

    @JsonProperty("vnp_TxnRef")
    private String txnRef;

    @JsonProperty("vnp_Amount")
    private Long amount;

    @JsonProperty("vnp_BankCode")
    private String bankCode;

    @JsonProperty("vnp_PayDate")
    private String payDate;

    @JsonProperty("vnp_TransactionNo")
    private String transactionNo;

    @JsonProperty("vnp_TransactionType")
    private String transactionType;

    // Trạng thái thật của giao dịch: "00" thành công, "01" đang chờ, "02" thất bại
    @JsonProperty("vnp_TransactionStatus")
    private String transactionStatus;

    @JsonProperty("vnp_OrderInfo")
    private String orderInfo;

    @JsonProperty("vnp_SecureHash")
    private String secureHash;
}
