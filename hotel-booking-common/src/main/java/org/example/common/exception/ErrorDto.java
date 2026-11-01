package org.example.common.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorDto {
    private String error;
    private String message;
    private int status;
    private LocalDateTime timestamp;
    private String path;
    private String traceId;
}
