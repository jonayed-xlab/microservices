package com.jbtech.orderservice.service;

import com.jbtech.orderservice.dto.InventoryResponse;
import com.jbtech.orderservice.dto.OrderLineitemsDto;
import com.jbtech.orderservice.dto.OrderRequest;
import com.jbtech.orderservice.model.Order;
import com.jbtech.orderservice.model.OrderLineitems;
import com.jbtech.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${service.url}")
    private String serviceUrl;

    public void placeOrder(OrderRequest orderRequest){
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineitems> orderLineItems = orderRequest.getOrderLineiteamsDtoList()
                .stream()
                .map(this::mapToData)
                .toList();

        order.setOrderLineiteamsList(orderLineItems);

        List<String> skuCodes = order.getOrderLineiteamsList().stream()
                .map(OrderLineitems::getSkuCode).toList();

        // call inventory service to check items are there.
        InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                .uri(serviceUrl,
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();
        boolean allProductsInStack = Arrays.stream(inventoryResponseArray).allMatch(InventoryResponse::getIsInStock);
        if (allProductsInStack)
            orderRepository.save(order);
        else
            throw new IllegalArgumentException("Product is not in stock!");
    }

    private OrderLineitems mapToData(OrderLineitemsDto orderLineiteamsDto) {
        return  OrderLineitems.builder()
                .skuCode(orderLineiteamsDto.getSkuCode())
                .price(orderLineiteamsDto.getPrice())
                .quantity(orderLineiteamsDto.getQuantity())
                .build();
    }
}
