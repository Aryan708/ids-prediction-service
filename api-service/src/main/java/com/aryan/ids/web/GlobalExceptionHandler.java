package com.aryan.ids.web;

import java.util.List;

import com.aryan.ids.client.MlServiceUnavailableException;
import com.aryan.ids.service.InvalidFeaturesException;
import com.aryan.ids.service.PredictionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps exceptions to RFC 7807 problem responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .sorted()
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed", errors);
    }

    @ExceptionHandler(InvalidFeaturesException.class)
    public ProblemDetail handleInvalidFeatures(InvalidFeaturesException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid flow features", e.getErrors());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleBadInput(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", List.of(rootMessage(e)));
    }

    @ExceptionHandler(PredictionNotFoundException.class)
    public ProblemDetail handleNotFound(PredictionNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage(), List.of());
    }

    @ExceptionHandler(MlServiceUnavailableException.class)
    public ProblemDetail handleModelDown(MlServiceUnavailableException e) {
        log.error("Model service call failed", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Model service is unavailable, try again shortly", List.of());
    }

    private static ProblemDetail problem(HttpStatus status, String detail, List<String> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        if (!errors.isEmpty()) {
            pd.setProperty("errors", errors);
        }
        return pd;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg.lines().findFirst().orElse(msg);
    }
}
