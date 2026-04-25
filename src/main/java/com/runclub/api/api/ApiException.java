package com.runclub.api.api;

/**
 * Base for application-level exceptions translated by {@link com.runclub.api.api.ApiExceptionHandler}.
 * Throw these (not bare {@link RuntimeException}) so the global handler can pick the
 * right HTTP status and Stripe-style error type.
 */
public class ApiException extends RuntimeException {
    private final ApiError.ErrorType type;
    private final String code;

    public ApiException(ApiError.ErrorType type, String code, String message) {
        super(message);
        this.type = type;
        this.code = code;
    }

    public ApiException(ApiError.ErrorType type, String message) {
        this(type, null, message);
    }

    public ApiError.ErrorType getType() { return type; }
    public String getCode() { return code; }

    public static ApiException notFound(String resource) {
        return new ApiException(ApiError.ErrorType.NOT_FOUND_ERROR, "resource_missing", resource + " not found");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(ApiError.ErrorType.INVALID_REQUEST_ERROR, "invalid_request", message);
    }

    public static ApiException missingField(String field) {
        return new ApiException(ApiError.ErrorType.INVALID_REQUEST_ERROR, "missing_field", field + " is required");
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ApiError.ErrorType.PERMISSION_ERROR, "forbidden", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ApiError.ErrorType.CONFLICT_ERROR, "resource_already_exists", message);
    }
}
