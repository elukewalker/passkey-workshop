package com.yubicolabs.passkey_rp.api;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.yubicolabs.passkey_rp.models.api.Error;

@ControllerAdvice
public class ControllerExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ControllerExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  private ResponseEntity<Error> handleGlobalError(Exception e) {
    log.error("Internal server error", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Error.builder().errorMessage("Internal server error. Please check relying party logs").build());
  }

  @ExceptionHandler(RuntimeException.class)
  private ResponseEntity<Error> handleRuntimeError(RuntimeException e) {
    log.error("Runtime error", e);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
        .body(Error.builder().errorMessage(e.getMessage()).build());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Error> handleException(HttpMessageNotReadableException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Error.builder().status("error").errorMessage(exception.getMessage()).build());
  }
}
