package com.runclub.api.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stripe-style error envelope.
 *
 * <pre>
 * { "error": { "type": "invalid_request_error", "code": "missing_field", "message": "..." } }
 * </pre>
 */
@Schema(name = "ApiError")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    @JsonProperty("error")
    public Detail error;

    public ApiError() {}

    public ApiError(Detail error) {
        this.error = error;
    }

    public static ApiError of(ErrorType type, String code, String message) {
        return new ApiError(new Detail(type.value, code, message));
    }

    public static ApiError of(ErrorType type, String message) {
        return of(type, null, message);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "ApiErrorDetail")
    public static class Detail {
        @JsonProperty("type")
        public String type;

        @JsonProperty("code")
        public String code;

        @JsonProperty("message")
        public String message;

        public Detail() {}

        public Detail(String type, String code, String message) {
            this.type = type;
            this.code = code;
            this.message = message;
        }
    }

    public enum ErrorType {
        INVALID_REQUEST_ERROR("invalid_request_error"),
        AUTHENTICATION_ERROR("authentication_error"),
        PERMISSION_ERROR("permission_error"),
        NOT_FOUND_ERROR("not_found_error"),
        CONFLICT_ERROR("conflict_error"),
        RATE_LIMIT_ERROR("rate_limit_error"),
        API_ERROR("api_error");

        public final String value;
        ErrorType(String value) { this.value = value; }
    }
}
