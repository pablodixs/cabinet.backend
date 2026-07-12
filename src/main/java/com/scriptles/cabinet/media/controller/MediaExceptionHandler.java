package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.external.ExternalMediaException;
import com.scriptles.cabinet.media.external.ExternalMediaRateLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MediaExceptionHandler {
    @ExceptionHandler(ExternalMediaRateLimitException.class)
    public ProblemDetail handleRateLimit(ExternalMediaRateLimitException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problem.setTitle("External provider rate limit exceeded");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(ExternalMediaException.class)
    public ProblemDetail handleExternalFailure(ExternalMediaException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        problem.setTitle("External provider failure");
        return problem;
    }
}
