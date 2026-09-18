package com.storex.order.model;

public record CreateShipmentRequest(
        String orderId,
        String receiverName,
        String receiverAddress) {
}

