package org.example.hotelmanagement.exception;

import org.example.common.exception.ErrorDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(basePackages = "org.example.hotelmanagement")
public class HotelExceptionHandler {

    @ExceptionHandler(HotelNotFoundException.class)
    public ResponseEntity<ErrorDto> handleHotelNotFoundException(HotelNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex, request);
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ErrorDto> handleRoomNotFoundException(RoomNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex, request);
    }

    @ExceptionHandler(RoomNotAvailableException.class)
    public ResponseEntity<ErrorDto> handleRoomNotAvailableException(RoomNotAvailableException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "Conflict", ex, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDto> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden", ex, request);
    }

    private ResponseEntity<ErrorDto> buildResponse(HttpStatus status, String error, Exception ex, HttpServletRequest request) {
        ErrorDto errorDto = ErrorDto.builder()
                .error(error)
                .message(ex.getMessage() != null ? ex.getMessage() : "An error occurred")
                .status(status.value())
                .timestamp(java.time.LocalDateTime.now())
                .path(request.getRequestURI())
                .traceId(getTraceId())
                .build();
        return ResponseEntity.status(status).body(errorDto);
    }

    private String getTraceId() {
        String traceId = org.slf4j.MDC.get("traceId");
        return traceId != null ? traceId : "unknown";
    }
}
