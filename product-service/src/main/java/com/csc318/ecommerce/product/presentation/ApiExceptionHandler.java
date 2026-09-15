package com.csc318.ecommerce.product.presentation;

import com.csc318.ecommerce.product.service.ApiException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;

@RestControllerAdvice
public class ApiExceptionHandler {
    record ErrorResponse(String code,String message,OffsetDateTime timestamp){}
    @ExceptionHandler(ApiException.class) ResponseEntity<ErrorResponse> api(ApiException ex){return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(ex.getCode(),ex.getMessage(),OffsetDateTime.now()));}
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex){return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR","Request validation failed",OffsetDateTime.now()));}
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException ex){return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST",ex.getMessage(),OffsetDateTime.now()));}
}
