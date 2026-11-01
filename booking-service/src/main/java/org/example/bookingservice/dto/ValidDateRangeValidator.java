package org.example.bookingservice.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that startDate is before endDate.
 */
public class ValidDateRangeValidator implements ConstraintValidator<ValidDateRange, BookingCreateRequest> {

    @Override
    public boolean isValid(BookingCreateRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getStartDate() == null || request.getEndDate() == null) {
            return true; // @NotNull handles null checks
        }
        return request.getStartDate().isBefore(request.getEndDate());
    }
}
