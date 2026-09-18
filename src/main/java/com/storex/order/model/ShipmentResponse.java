package com.storex.order.model;

public record ShipmentResponse(String orderId, String trackingCode, String status) {
}

