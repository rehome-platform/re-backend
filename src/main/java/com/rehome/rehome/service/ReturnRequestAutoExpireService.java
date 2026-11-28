package com.rehome.rehome.service;

public interface ReturnRequestAutoExpireService {

    void cancelNotHandedOverRequests();

    void expireUncollectedInvalidReturns();

    void expireOverdueShipBackPayments();
}
