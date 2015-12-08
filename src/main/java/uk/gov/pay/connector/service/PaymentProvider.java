package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.GatewayAccount;

import java.util.function.Consumer;
import java.util.function.Function;

public interface PaymentProvider {

    AuthorisationResponse authorise(AuthorisationRequest request);

    CaptureResponse capture(CaptureRequest request);

    CancelResponse cancel(CancelRequest request);

    StatusUpdates handleNotification(String notificationPayload, Function<String, GatewayAccount> accountFinder, Consumer<StatusUpdates> accountUpdater);
}
