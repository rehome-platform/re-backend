package com.rehome.rehome.dto.response;

// RspCode/Message theo đúng format VNPay yêu cầu trả về từ IPN handler; sai format sẽ bị VNPay
// coi là xác nhận thất bại và gọi lại nhiều lần.
public record VnPayIpnResult(String rspCode, String message) {
}
