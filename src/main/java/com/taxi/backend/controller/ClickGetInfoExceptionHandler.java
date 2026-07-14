package com.taxi.backend.controller;

import com.taxi.backend.service.ClickGetInfoError;
import com.taxi.backend.service.ClickGetInfoResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Keeps malformed GetInfo requests out of the application's generic error schema. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AdvancedShopController.class)
public class ClickGetInfoExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformedBody(HttpMessageNotReadableException ignored) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ClickGetInfoResponse.error(ClickGetInfoError.MALFORMED_REQUEST));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> unsupportedMedia(HttpMediaTypeNotSupportedException ignored) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ClickGetInfoResponse.error(ClickGetInfoError.MALFORMED_REQUEST));
    }
}
