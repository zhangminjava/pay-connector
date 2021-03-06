package uk.gov.pay.connector.model;

import org.apache.http.NameValuePair;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public class EvaluatedChargeStatusNotification<T> implements EvaluatedNotification<T> {

    private final Notification<T> notification;
    private final ChargeStatus chargeStatus;

    public EvaluatedChargeStatusNotification(Notification<T> notification, ChargeStatus chargeStatus) {
        this.notification = notification;
        this.chargeStatus = chargeStatus;
    }

    @Override
    public String getTransactionId() {
        return notification.getTransactionId();
    }

    @Override
    public String getReference() {
        return notification.getReference();
    }

    @Override
    public T getStatus() {
        return notification.getStatus();
    }

    @Override
    public ZonedDateTime getGatewayEventDate() { return notification.getGatewayEventDate(); }

    @Override
    public Optional<List<NameValuePair>> getPayload() {
        return notification.getPayload();
    }

    @Override
    public boolean isOfChargeType() {
        return true;
    }

    public ChargeStatus getChargeStatus() {
        return chargeStatus;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("EvaluatedChargeStatusNotification[")
                .append(notification)
                .append(" mapping to ")
                .append(chargeStatus)
                .append("]")
                .toString();
    }

}
