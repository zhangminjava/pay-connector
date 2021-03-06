package uk.gov.pay.connector.service;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.RefundException;
import uk.gov.pay.connector.model.ErrorType;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.RefundRequest;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.service.smartpay.SmartpayRefundResponse;
import uk.gov.pay.connector.service.transaction.TransactionFlow;
import uk.gov.pay.connector.service.worldpay.WorldpayRefundResponse;

import java.util.Optional;

import static com.google.common.collect.Maps.newHashMap;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;
import static uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.service.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.service.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.service.PaymentGatewayName.WORLDPAY;

@RunWith(MockitoJUnitRunner.class)
public class ChargeRefundServiceTest {

    private ChargeRefundService chargeRefundService;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private ChargeDao mockChargeDao;
    @Mock
    private RefundDao mockRefundDao;
    @Mock
    private PaymentProviders mockProviders;
    @Mock
    private PaymentProvider mockProvider;

    @Before
    public void setUp() {
        when(mockProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockProvider);
        when(mockProvider.getExternalChargeRefundAvailability(any(ChargeEntity.class))).thenReturn(EXTERNAL_AVAILABLE);
        chargeRefundService = new ChargeRefundService(mockChargeDao, mockRefundDao, mockProviders, TransactionFlow::new);
    }

    public void setupWorldpayMock(String reference, String errorCode) {
        WorldpayRefundResponse worldpayResponse = mock(WorldpayRefundResponse.class);
        when(worldpayResponse.getReference()).thenReturn(Optional.ofNullable(reference));
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        GatewayResponseBuilder<WorldpayRefundResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse refundResponse = gatewayResponseBuilder
                .withResponse(worldpayResponse)
                .build();
        when(mockProvider.refund(any())).thenReturn(refundResponse);
    }

    public void setupSmartpayMock(String reference, String errorCode) {
        SmartpayRefundResponse smartpayRefundResponse = mock(SmartpayRefundResponse.class);
        when(smartpayRefundResponse.getReference()).thenReturn(Optional.ofNullable(reference));
        when(smartpayRefundResponse.getErrorCode()).thenReturn(errorCode);
        GatewayResponseBuilder<SmartpayRefundResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse refundResponse = gatewayResponseBuilder
                .withResponse(smartpayRefundResponse)
                .build();
        when(mockProvider.refund(any())).thenReturn(refundResponse);
    }

    @Test
    public void shouldRefundSuccessfully_forWorldpay() {
        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "worldpay";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withStatus(CAPTURED)
                .build();

        RefundEntity refundEntity = aValidRefundEntity().withCharge(charge).build();
        RefundEntity spiedRefundEntity = spy(refundEntity);

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));

        when(mockChargeDao.merge(charge)).thenReturn(charge);

        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);
        setupWorldpayMock(spiedRefundEntity.getExternalId(), null);

        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount())).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().isPresent(), is(false));

        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockChargeDao).merge(charge);
        verify(mockRefundDao).persist(argThat(aRefundEntity(amount, charge)));
        verify(mockProvider).refund(argThat(aRefundRequestWith(charge, amount)));
        verify(mockRefundDao).merge(any(RefundEntity.class));
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_SUBMITTED);
        verify(spiedRefundEntity).setReference(refundEntity.getExternalId());
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao);
    }


    @Test
    public void shouldRefundSuccessfully_forSmartpay() {
        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "smartpay";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withStatus(CAPTURED)
                .build();

        RefundEntity refundEntity = aValidRefundEntity().withCharge(charge).build();
        RefundEntity spiedRefundEntity = spy(refundEntity);

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));

        when(mockChargeDao.merge(charge)).thenReturn(charge);

        when(mockProviders.byName(SMARTPAY)).thenReturn(mockProvider);
        setupSmartpayMock("refund-pspReference", null);

        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount())).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().isPresent(), is(false));

        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockChargeDao).merge(charge);
        verify(mockRefundDao).persist(argThat(aRefundEntity(amount, charge)));
        verify(mockProvider).refund(argThat(aRefundRequestWith(charge, amount)));
        verify(mockRefundDao).merge(any(RefundEntity.class));
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_SUBMITTED);
        verify(spiedRefundEntity).setReference("refund-pspReference");
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao);
    }

    @Test
    public void shouldOverrideGeneratedReferenceIfProviderReturnAReference() {
        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "worldpay";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        String generatedReference = "generated-reference";

        String providerReference = "worldpay-reference";
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withStatus(CAPTURED)
                .build();

        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withReference(generatedReference).withCharge(charge).build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));
        when(mockChargeDao.merge(charge)).thenReturn(charge);
        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);
        setupWorldpayMock(providerReference, null);

        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount())).get();
        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));
        verify(spiedRefundEntity).setReference(providerReference);
    }

    @Test
    public void shouldStoreEmptyGatewayReferenceIfGatewayReturnsAnError() {
        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "worldpay";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        String generatedReference = "generated-reference";
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withStatus(CAPTURED)
                .build();

        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withReference(generatedReference).withCharge(charge).build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));
        when(mockChargeDao.merge(charge)).thenReturn(charge);
        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);
        setupWorldpayMock(null, "error-code");

        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount())).get();
        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(false));
        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));
        verify(spiedRefundEntity).setReference("");
    }

    @Test
    public void shouldRefundSuccessfullyForSandbox() {

        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "sandbox";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        String reference = "reference";
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId(reference)
                .withStatus(CAPTURED)
                .build();

        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withCharge(charge).build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));

        when(mockChargeDao.merge(charge)).thenReturn(charge);

        when(mockProviders.byName(SANDBOX)).thenReturn(mockProvider);

        setupWorldpayMock(reference, null);

        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount())).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().isPresent(), is(false));

        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockChargeDao).merge(charge);
        verify(mockRefundDao).persist(argThat(aRefundEntity(amount, charge)));
        verify(mockProvider).refund(argThat(aRefundRequestWith(charge, amount)));
        verify(mockRefundDao).merge(any(RefundEntity.class));
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUNDED);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao);
    }

    @Test
    public void shouldFailWhenChargeNotFound() {
        String externalChargeId = "chargeId";
        Long accountId = 2L;

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.empty());

        try {
            chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(100L, 0));
            fail("Should throw an exception here");
        } catch (Exception e) {
            assertEquals(e.getClass(), ChargeNotFoundRuntimeException.class);
        }

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao, mockProviders, mockProvider);
    }

    @Test
    public void shouldFailWhenChargeRefundIsNotAvailable() {
        String externalChargeId = "chargeId";
        Long accountId = 2L;
        GatewayAccountEntity account = new GatewayAccountEntity("sandbox", newHashMap(), TEST);
        account.setId(accountId);
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transactionId")
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));
        when(mockChargeDao.merge(charge)).thenReturn(charge);

        try {
            chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(100L, 0));
            fail("Should throw an exception here");
        } catch (Exception e) {
            assertEquals(e.getClass(), RefundException.class);
        }

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockChargeDao).merge(charge);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao);
    }

    @Test
    public void shouldUpdateRefundRecordToFailWhenRefundFails() {

        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "worldpay";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        ChargeEntity capturedCharge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withStatus(CAPTURED)
                .build();

        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(capturedCharge));
        when(mockChargeDao.merge(capturedCharge)).thenReturn(capturedCharge);
        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);

        setupWorldpayMock(null, "error-code");
        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);
        when(mockChargeDao.merge(capturedCharge)).thenReturn(capturedCharge);
        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, capturedCharge.getAmount())).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isFailed(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().get().getMessage(), is("[error-code]"));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().get().getErrorType(), is(ErrorType.GENERIC_GATEWAY_ERROR));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockChargeDao).merge(capturedCharge);
        verify(mockRefundDao).persist(argThat(aRefundEntity(amount, capturedCharge)));
        verify(mockProvider).refund(argThat(aRefundRequestWith(capturedCharge, amount)));
        verify(mockRefundDao).merge(any(RefundEntity.class));
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_ERROR);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao);
    }


    private ArgumentMatcher<RefundEntity> aRefundEntity(long amount, ChargeEntity chargeEntity) {
        return object -> {
            RefundEntity refundEntity = ((RefundEntity) object);
            return refundEntity.getAmount() == amount &&
                    refundEntity.getChargeEntity().equals(chargeEntity);
        };
    }

    private ArgumentMatcher<RefundGatewayRequest> aRefundRequestWith(ChargeEntity capturedCharge, long amountInPence) {
        return object -> {
            RefundGatewayRequest refundGatewayRequest = ((RefundGatewayRequest) object);
            return refundGatewayRequest.getGatewayAccount().equals(capturedCharge.getGatewayAccount()) &&
                    refundGatewayRequest.getTransactionId().equals(capturedCharge.getGatewayTransactionId()) &&
                    refundGatewayRequest.getAmount().equals(String.valueOf(amountInPence));
        };
    }
}
