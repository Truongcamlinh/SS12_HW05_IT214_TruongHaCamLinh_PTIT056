package com.storex.order.client;

import com.storex.order.model.CreateShipmentRequest;
import com.storex.order.model.ShipmentResponse;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class GhtkClient {
    private final WebClient webClient;

    public GhtkClient(WebClient.Builder builder,
                      @Value("${ghtk.api.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    @Retry(name = "ghtkClient", fallbackMethod = "shipmentFallback")
    public Mono<ShipmentResponse> createShipment(CreateShipmentRequest request) {
        return webClient.post()
                .uri("/api/shipments")
                .header("Idempotency-Key", request.orderId())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ShipmentResponse.class)
                .timeout(Duration.ofSeconds(3));
    }

    private Mono<ShipmentResponse> shipmentFallback(
            CreateShipmentRequest request,
            Throwable error) {
        return Mono.just(new ShipmentResponse(
                request.orderId(),
                null,
                "PENDING_RETRY"));
    }
}

