package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import fj.data.Either;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.domain.*;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.service.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.service.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.util.AuthUtils;

import javax.persistence.OptimisticLockException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus.IN_PROGRESS;

@RunWith(MockitoJUnitRunner.class)
public class CardAuthoriseServiceTest extends CardServiceTest {

    public static final String PA_REQ_VALUE_FROM_PROVIDER = "pa-req-value-from-provider";
    public static final String ISSUER_URL_FROM_PROVIDER = "issuer-url-from-provider";
    public static final String SESSION_IDENTIFIER = "session-identifier";

    private final Auth3dsDetailsFactory auth3dsDetailsFactory = new Auth3dsDetailsFactory();

    @Mock
    private Future<Either<Error, GatewayResponse>> mockFutureResponse;

    private ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
    private ChargeEntity reloadedCharge = spy(charge);

    private CardAuthoriseService cardAuthorisationService;
    private CardExecutorService mockExecutorService = mock(CardExecutorService.class);

    @Before
    public void setUpCardAuthorisationService() {
        Environment mockEnvironment = mock(Environment.class);
        mockMetricRegistry = mock(MetricRegistry.class);
        Counter mockCounter = mock(Counter.class);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);

        cardAuthorisationService = new CardAuthoriseService(mockedChargeDao, mockedCardTypeDao, mockedProviders, mockExecutorService,
                auth3dsDetailsFactory, mockEnvironment);
    }

    public void setupMockExecutorServiceMock() {
        doAnswer(invocation -> Pair.of(COMPLETED, ((Supplier) invocation.getArguments()[0]).get()))
                .when(mockExecutorService).execute(any(Supplier.class));
    }

    private void setupPaymentProviderMock(String transactionId, AuthoriseStatus authoriseStatus, String errorCode) {
        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        when(worldpayResponse.getTransactionId()).thenReturn(transactionId);
        when(worldpayResponse.authoriseStatus()).thenReturn(authoriseStatus);
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        GatewayResponseBuilder<WorldpayOrderStatusResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse authorisationResponse = gatewayResponseBuilder
                .withResponse(worldpayResponse)
                .withSessionIdentifier(SESSION_IDENTIFIER)
                .build();
        when(mockedPaymentProvider.authorise(any())).thenReturn(authorisationResponse);
    }

    private void setupPaymentProviderMockFor3ds() {
        WorldpayOrderStatusResponse worldpayResponse = new WorldpayOrderStatusResponse();
        worldpayResponse.set3dsPaRequest(PA_REQ_VALUE_FROM_PROVIDER);
        worldpayResponse.set3dsIssuerUrl(ISSUER_URL_FROM_PROVIDER);
        GatewayResponseBuilder<WorldpayOrderStatusResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse worldpay3dsResponse = gatewayResponseBuilder
                .withResponse(worldpayResponse)
                .build();
        when(mockedPaymentProvider.authorise(any())).thenReturn(worldpay3dsResponse);
    }

    @Test
    public void shouldRespondAuthorisationSuccess() throws Exception {
        String transactionId = "transaction-id";
        AuthCardDetails authCardDetails = AuthUtils.aValidAuthorisationDetails();

        GatewayResponse response = anAuthorisationSuccessResponse(charge, reloadedCharge, transactionId, authCardDetails);

        assertThat(response.isSuccessful(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_SUCCESS.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(transactionId));
    }

    @Test(expected = ConflictRuntimeException.class)
    public void shouldRespondAuthorisationFailedWhen3dsRequiredConflictingConfigurationOfCardTypeWithGatewayAccount() throws Exception {

        AuthCardDetails authCardDetails = AuthUtils.aValidAuthorisationDetails();

        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity();
        CardTypeEntity cardTypeEntity = new CardTypeEntity();
        cardTypeEntity.setRequires3ds(true);
        cardTypeEntity.setBrand(authCardDetails.getCardBrand());
        gatewayAccountEntity.setType(GatewayAccountEntity.Type.LIVE);
        gatewayAccountEntity.setGatewayName("worldpay");
        gatewayAccountEntity.setRequires3ds(false);

        ChargeEntity charge = ChargeEntityFixture
                .aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withStatus(ENTERING_CARD_DETAILS)
                .build();
        ChargeEntity reloadedCharge = spy(charge);

        when(mockedCardTypeDao.findByBrand(authCardDetails.getCardBrand())).thenReturn(newArrayList(cardTypeEntity));
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
        when(mockedChargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge, Optional.empty())).thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertThat(response.isSuccessful(), is(false));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_ABORTED.toString()));
    }

    @Test
    public void shouldRespondWith3dsResponseFor3dsOrders() {
        String transactionId = "transaction-id";
        AuthCardDetails authCardDetails = AuthUtils.aValidAuthorisationDetails();

        GatewayResponse response = anAuthorisation3dsRequiredResponse(charge, reloadedCharge, transactionId, authCardDetails);

        assertThat(response.isSuccessful(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(transactionId));
        assertThat(reloadedCharge.get3dsDetails().getPaRequest(), is(PA_REQ_VALUE_FROM_PROVIDER));
        assertThat(reloadedCharge.get3dsDetails().getIssuerUrl(), is(ISSUER_URL_FROM_PROVIDER));
    }

    @Test
    public void shouldRespondAuthorisationSuccess_whenTransactionIdIsGenerated() throws Exception {
        String generatedTransactionId = "generated-transaction-id";
        String providerTransactionId = "provider-transaction-id";

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(providerTransactionId, AuthoriseStatus.AUTHORISED, null);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        assertThat(response.isSuccessful(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_SUCCESS.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(providerTransactionId));
    }

    @Test
    public void shouldRetainGeneratedTransactionIdIfAuthorisationAborted() throws Exception {
        String generatedTransactionId = "generated-transaction-id";

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));

        setupMockExecutorServiceMock();

        when(mockedPaymentProvider.authorise(any())).thenThrow(RuntimeException.class);

        try {
            cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
            fail("Won’t get this far");
        } catch (RuntimeException e) {
            assertThat(reloadedCharge.getGatewayTransactionId(), is(generatedTransactionId));
        }
    }


    @Test
    public void shouldRespondAuthorisationRejected() throws Exception {
        String transactionId = "transaction-id";
        ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
        ChargeEntity reloadedCharge = spy(charge);
        GatewayResponse response = anAuthorisationRejectedResponse(charge, reloadedCharge);

        assertThat(response.isSuccessful(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_REJECTED.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(transactionId));
    }

    @Test
    public void shouldRespondAuthorisationCancelled() throws Exception {
        String transactionId = "transaction-id";
        ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
        ChargeEntity reloadedCharge = spy(charge);
        GatewayResponse response = anAuthorisationCancelledResponse(charge, reloadedCharge);

        assertThat(response.isSuccessful(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_CANCELLED.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(transactionId));
    }

    @Test
    public void shouldRespondAuthorisationError() throws Exception {

        GatewayResponse response = anAuthorisationErrorResponse(charge, reloadedCharge);

        assertThat(response.isFailed(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_ERROR.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(nullValue()));
    }

    @Test
    public void shouldStoreCardDetailsIfAuthorisationSuccess() {
        String transactionId = "transaction-id";
        AuthCardDetails authCardDetails = AuthUtils.aValidAuthorisationDetails();
        anAuthorisationSuccessResponse(charge, reloadedCharge, transactionId, authCardDetails);

        assertThat(reloadedCharge.getCardDetails(), is(notNullValue()));
    }

    @Test
    public void shouldStoreCardDetailsEvenIfAuthorisationRejected() {
        anAuthorisationRejectedResponse(charge, reloadedCharge);
        assertThat(reloadedCharge.getCardDetails(), is(notNullValue()));
    }

    @Test
    public void shouldStoreCardDetailsEvenIfInAuthorisationError() {
        anAuthorisationErrorResponse(charge, reloadedCharge);
        assertThat(reloadedCharge.getCardDetails(), is(notNullValue()));
    }

    @Test
    public void shouldStoreProviderSessionIdIfAuthorisationSuccess() {
        anAuthorisationSuccessResponseWithTransaction(charge, reloadedCharge);
        assertThat(reloadedCharge.getProviderSessionId(), is(SESSION_IDENTIFIER));
    }

    @Test
    public void shouldStoreProviderSessionIdIfAuthorisationRejected() {
        anAuthorisationRejectedResponse(charge, reloadedCharge);

        assertThat(reloadedCharge.getCardDetails(), is(notNullValue()));
        assertThat(reloadedCharge.getProviderSessionId(), is(SESSION_IDENTIFIER));
    }

    @Test
    public void shouldNotProviderSessionIdEvenIfInAuthorisationError() {
        anAuthorisationErrorResponse(charge, reloadedCharge);

        assertThat(reloadedCharge.getCardDetails(), is(notNullValue()));
        assertNull(reloadedCharge.getProviderSessionId());
    }

    @Test
    public void authoriseShouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenTimeout() throws Exception {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);
        when(mockExecutorService.execute(any())).thenReturn(Pair.of(IN_PROGRESS, null));

        try {
            cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
            fail("Exception not thrown.");
        } catch (OperationAlreadyInProgressRuntimeException e) {
            Map<String, String> expectedMessage = ImmutableMap.of("message", format("Authorisation for charge already in progress, %s", charge.getExternalId()));
            assertThat(e.getResponse().getEntity(), is(expectedMessage));
        }
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void shouldThrowAChargeNotFoundRuntimeExceptionWhenChargeDoesNotExist() {
        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";

        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());

        cardAuthorisationService.doAuthorise(chargeId, AuthUtils.aValidAuthorisationDetails());
    }

    @Test(expected = OperationAlreadyInProgressRuntimeException.class)
    public void shouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenStatusIsAuthorisationReady() {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_READY);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);

        setupMockExecutorServiceMock();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void shouldThrowAnIllegalStateRuntimeExceptionWhenInvalidStatus() throws Exception {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.CREATED);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);

        setupMockExecutorServiceMock();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = ConflictRuntimeException.class)
    public void shouldThrowAConflictRuntimeException() throws Exception {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenThrow(new OptimisticLockException());

        setupMockExecutorServiceMock();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    private GatewayResponse anAuthorisationRejectedResponse(ChargeEntity charge, ChargeEntity reloadedCharge) {
        return authorisationResponse(charge, reloadedCharge, AuthoriseStatus.REJECTED);
    }

    private GatewayResponse anAuthorisationCancelledResponse(ChargeEntity charge, ChargeEntity reloadedCharge) {
        return authorisationResponse(charge, reloadedCharge, AuthoriseStatus.CANCELLED);
    }

    private GatewayResponse authorisationResponse(ChargeEntity charge, ChargeEntity reloadedCharge, AuthoriseStatus authoriseStatus) {
        String transactionId = "transaction-id";

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(transactionId, authoriseStatus, null);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());

        return cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
    }

    private GatewayResponse anAuthorisationSuccessResponse(ChargeEntity charge, ChargeEntity reloadedCharge, String transactionId, AuthCardDetails authCardDetails) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(transactionId, AuthoriseStatus.AUTHORISED, null);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());

        return cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);
    }

    private GatewayResponse anAuthorisationSuccessResponseWithTransaction(ChargeEntity charge, ChargeEntity reloadedCharge) {
        String transactionId = "transaction-id";
        AuthCardDetails authCardDetails = AuthUtils.aValidAuthorisationDetails();

        return anAuthorisationSuccessResponse(charge, reloadedCharge, transactionId, authCardDetails);
    }


    private GatewayResponse anAuthorisation3dsRequiredResponse(ChargeEntity charge, ChargeEntity reloadedCharge, String transactionId, AuthCardDetails authCardDetails) {

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();
        setupPaymentProviderMockFor3ds();

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(transactionId));

        return cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);
    }

    private GatewayResponse anAuthorisationErrorResponse(ChargeEntity charge, ChargeEntity reloadedCharge) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(null, AuthoriseStatus.REJECTED, "error-code");

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());

        return cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
    }
}
