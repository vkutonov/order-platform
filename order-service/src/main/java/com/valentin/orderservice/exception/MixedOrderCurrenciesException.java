package com.valentin.orderservice.exception;

import java.util.Set;

public final class MixedOrderCurrenciesException extends OrderServiceException {

    private final Set<String> currencies;

    public MixedOrderCurrenciesException(Set<String> currencies) {
        super(
                ApiErrorCode.MIXED_ORDER_CURRENCIES,
                "Order items must have the same currency: currencies=" + currencies
        );
        this.currencies = Set.copyOf(currencies);
    }

    public MixedOrderCurrenciesException(String orderCurrency, String itemCurrency) {
        this(Set.of(orderCurrency, itemCurrency));
    }

    public Set<String> getCurrencies() {
        return currencies;
    }
}
