package uk.gov.pay.connector.model;

import static uk.gov.pay.connector.model.GatewayErrorType.GenericGatewayError;

public class GatewayError {
    private String message;
    private GatewayErrorType errorType;

    public GatewayError(String message, GatewayErrorType errorType) {
        this.message = message;
        this.errorType = errorType;
    }

    public static GatewayError baseGatewayError(String msg) {
        return new GatewayError(msg, GenericGatewayError);
    }

    public String getMessage() {
        return message;
    }

    public GatewayErrorType getErrorType() {
        return errorType;
    }
}