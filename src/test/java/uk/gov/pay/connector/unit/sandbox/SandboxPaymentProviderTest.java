package uk.gov.pay.connector.unit.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import uk.gov.pay.connector.model.StatusUpdates;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class SandboxPaymentProviderTest {

    private SandboxPaymentProvider sandboxClient = new SandboxPaymentProvider(new ObjectMapper());

    @Test
    public void shouldSuccessfullyParseANotification() throws Exception {
        StatusUpdates statusUpdates = sandboxClient.newStatusFromNotification(toJson(ImmutableMap.of(
                "transaction_id", "transaction",
                "status", AUTHORISATION_REJECTED.getValue())));

        assertThat(statusUpdates.successful(), is(true));
        assertThat(statusUpdates.getStatusUpdates(), hasItem(Pair.of("transaction", AUTHORISATION_REJECTED)));
        assertThat(statusUpdates.getResponseForProvider(), is("OK"));
    }

    @Test
    public void shouldIgnoreUnknownStatuses() throws Exception {
        StatusUpdates statusUpdates = sandboxClient.newStatusFromNotification(toJson(ImmutableMap.of(
                "transaction_id", "transaction",
                "status", "UNKNOWN_STATUS")));

        assertThat(statusUpdates.successful(), is(true));
        assertThat(statusUpdates.getStatusUpdates(), empty());
        assertThat(statusUpdates.getResponseForProvider(), is("OK"));
    }

    @Test
    public void shouldIgnoreMalformedJson() throws Exception {
        StatusUpdates statusUpdates = sandboxClient.newStatusFromNotification("{");

        assertThat(statusUpdates.successful(), is(true));
        assertThat(statusUpdates.getStatusUpdates(), empty());
        assertThat(statusUpdates.getResponseForProvider(), is("OK"));
    }

    @Test
    public void shouldIgnoreJsonWithMissingFields() throws Exception {
        StatusUpdates statusUpdates = sandboxClient.newStatusFromNotification("{}");

        assertThat(statusUpdates.successful(), is(true));
        assertThat(statusUpdates.getStatusUpdates(), empty());
        assertThat(statusUpdates.getResponseForProvider(), is("OK"));
    }




}
