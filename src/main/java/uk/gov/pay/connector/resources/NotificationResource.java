package uk.gov.pay.connector.resources;

import io.dropwizard.auth.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.auth.BasicAuthUser;
import uk.gov.pay.connector.dao.IChargeDao;
import uk.gov.pay.connector.dao.IGatewayAccountDao;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.model.StatusUpdates;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.ChargeStatusBlacklist;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.PaymentProviders;
import uk.gov.pay.connector.util.NotificationUtil;

import javax.inject.Inject;
import javax.annotation.security.PermitAll;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static javax.ws.rs.core.Response.Status.BAD_GATEWAY;

@Path("/")
public class NotificationResource {

    private static final Logger logger = LoggerFactory.getLogger(NotificationResource.class);
    private PaymentProviders providers;
    private IChargeDao chargeDao;
    private IGatewayAccountDao accountDao;
    private NotificationUtil notificationUtil = new NotificationUtil(new ChargeStatusBlacklist());

    @Inject
    public NotificationResource(PaymentProviders providers, IChargeDao chargeDao, IGatewayAccountDao accountDao) {
        this.providers = providers;
        this.chargeDao = chargeDao;
        this.accountDao = accountDao;
    }

    @POST
    @PermitAll
    @Path("v1/api/notifications/smartpay")
    public Response authoriseSmartpayNotifications(String notification) throws IOException {
        return handleNotification("smartpay", notification);
    }

    @POST
    @Path("v1/api/notifications/{provider}")
    public Response handleNotification(@PathParam("provider") String provider, String notification) {

        logger.info("Received notification from " + provider + ": " + notification);

        PaymentProvider paymentProvider = providers.resolve(provider);
        StatusUpdates statusUpdates = paymentProvider.handleNotification(notification, notificationUtil::payloadChecks, findAccountByTransactionId(provider), accountUpdater(provider));

        if (!statusUpdates.successful()) {
            return Response.status(BAD_GATEWAY).build();
        }

        return Response.ok(statusUpdates.getResponseForProvider()).build();
    }

    private Consumer<StatusUpdates> accountUpdater(String provider) {
        return statusUpdates ->
                statusUpdates.getStatusUpdates().forEach(update -> updateCharge(chargeDao, provider, update.getKey(), update.getValue()));
    }


    private Function<String, Optional<GatewayAccount>> findAccountByTransactionId(String provider) {
        return transactionId -> {
            Optional<String> accountId = chargeDao.findAccountByTransactionId(provider, transactionId);

            if (accountId.isPresent()) {
                Optional<GatewayAccount> gatewayAccount = accountDao.findById(accountId.get());

                if (gatewayAccount.isPresent())
                    return gatewayAccount;
            }

            logger.error("Could not find account for transaction id " + transactionId);
            return Optional.empty();
        };
    }

    private static void updateCharge(IChargeDao chargeDao, String provider, String transactionId, ChargeStatus value) {
        try {
            chargeDao.updateStatusWithGatewayInfo(provider, transactionId, value);
        } catch (PayDBIException e) {
            logger.error("Error when trying to update transaction id " + transactionId + " to status " + value, e);
        }
    }
}