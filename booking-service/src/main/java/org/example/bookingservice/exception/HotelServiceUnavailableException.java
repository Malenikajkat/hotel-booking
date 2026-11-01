package org.example.bookingservice.exception;

/**
 * Exception thrown when Hotel Management Service is unavailable or returns an error.
 * Used for compensation handling in the Saga pattern.
 */
public class HotelServiceUnavailableException extends RuntimeException {

    public HotelServiceUnavailableException(String message) {
        super(message);
    }

    public HotelServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
