package com.example.ticketsystem.config;

import com.example.ticketsystem.model.BuyResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<BuyResponse> handleDuplicateKey(
            DuplicateKeyException e
    ) {
        return ResponseEntity.status(409).body(
                new BuyResponse(
                        false,
                        "同一使用者不能重複搶同一活動",
                        null,
                        null
                )
        );
    }
}
