package com.example.ticketsystem.service;

import com.example.ticketsystem.model.BuyResponse;
import org.springframework.stereotype.Service;

@Service
public class SynchronizedBuyService {

    private final UnsafeBuyService unsafeBuyService;

    public SynchronizedBuyService(UnsafeBuyService unsafeBuyService) {
        this.unsafeBuyService = unsafeBuyService;
    }

    // 只鎖「目前這一個 JVM 裡」的這個 Service instance。
    // 兩個 Container / Pod = 兩個 JVM = 兩把不同的鎖。
    public synchronized BuyResponse buy(Long eventId, String userId) {
        return unsafeBuyService.buy(eventId, userId);
    }
}
