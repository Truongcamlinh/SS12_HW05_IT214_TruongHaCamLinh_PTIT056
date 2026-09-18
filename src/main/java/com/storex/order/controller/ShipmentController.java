package com.storex.order.controller;

import com.storex.order.client.GhtkClient;
import com.storex.order.model.CreateShipmentRequest;
import com.storex.order.model.ShipmentResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/orders/shipments")
public class ShipmentController {
    private final GhtkClient ghtkClient;

    public ShipmentController(GhtkClient ghtkClient) {
        this.ghtkClient = ghtkClient;
    }

    @PostMapping
    public Mono<ShipmentResponse> create(@RequestBody CreateShipmentRequest request) {
        return ghtkClient.createShipment(request);
    }
}

