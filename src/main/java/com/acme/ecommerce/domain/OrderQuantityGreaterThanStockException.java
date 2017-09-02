package com.acme.ecommerce.domain;

public class OrderQuantityGreaterThanStockException extends RuntimeException {
    public OrderQuantityGreaterThanStockException(Product product) {
        super("Insufficient product in stock. There are only " + product.getQuantity() + " of " + product.getName() + " available.");
    }
}
