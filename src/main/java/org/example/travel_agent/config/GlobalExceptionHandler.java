package org.example.travel_agent.config;

import jakarta.servlet.http.HttpServletRequest;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.Exceptions.ServerException;
import org.example.travel_agent.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String DEFAULT_ERROR_MSG = "系统异常";
    private static final String SSE_CONTENT_TYPE = "text/event-stream";

    @ExceptionHandler(ClientException.class)
    public ResponseEntity<?> handleClientException(ClientException e, HttpServletRequest request) {
        String msg = resolveMessage(e);
        if (isSseRequest(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(msg));
    }

    @ExceptionHandler(ServerException.class)
    public ResponseEntity<?> handleServerException(ServerException e, HttpServletRequest request) {
        String msg = resolveMessage(e);
        log.error("Server exception: {}", msg, e);
        if (isSseRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e, HttpServletRequest request) {
        String msg = resolveMessage(e);
        log.error("Unhandled exception: {}", msg, e);
        if (isSseRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(msg));
    }

    private String resolveMessage(Throwable e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? DEFAULT_ERROR_MSG : msg;
    }

    private boolean isSseRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(SSE_CONTENT_TYPE)) {
            return true;
        }
        return request.getRequestURI() != null && request.getRequestURI().contains("/api/chat/deepThink");
    }
}
