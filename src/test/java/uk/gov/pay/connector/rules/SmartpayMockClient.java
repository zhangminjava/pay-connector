package uk.gov.pay.connector.rules;

import com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class SmartpayMockClient {

    public SmartpayMockClient() {
    }

    public void mockAuthorisationSuccess() {
        String gatewayTransactionId = randomId();
        String authoriseResponse = loadFromTemplate("authorisation-success-response.xml", gatewayTransactionId);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockAuthorisationFailure() {
        String gatewayTransactionId = randomId();
        String authoriseResponse = loadFromTemplate("authorisation-failed-response.xml", gatewayTransactionId);
        paymentServiceResponse(authoriseResponse);
    }

    public void mockCaptureResponse() {
        String gatewayTransactionId = randomId();
        String captureResponse = loadFromTemplate("capture-success-response.xml", gatewayTransactionId);
        paymentServiceResponse(captureResponse);
    }

    public void mockCancelResponse(String gatewayTransactionId) {
        String cancelResponse = loadFromTemplate("cancel-success-response.xml", gatewayTransactionId);
        paymentServiceResponse(cancelResponse);
    }

    public void mockErrorResponse() {
        String errorResponse = loadFromTemplate("error-response.xml", "");
        paymentServiceResponse(errorResponse);
    }

    private void paymentServiceResponse(String responseBody) {
        stubFor(
                post(urlPathEqualTo("/pal/servlet/soap/Payment"))
                        .willReturn(
                                aResponse()
                                        .withHeader(CONTENT_TYPE, TEXT_XML)
                                        .withStatus(200)
                                        .withBody(responseBody)
                        )
        );
    }

    private String loadFromTemplate(String fileName, String gatewayTransactionId) {
        try {
            return Resources.toString(Resources.getResource("templates/smartpay/" + fileName), Charset.defaultCharset())
                    .replace("{{transactionId}}", gatewayTransactionId);
        } catch (IOException e) {
            throw new RuntimeException("Could not load template", e);
        }
    }
}
