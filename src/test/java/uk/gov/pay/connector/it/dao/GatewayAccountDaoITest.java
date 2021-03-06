package uk.gov.pay.connector.it.dao;

import com.google.common.collect.ImmutableMap;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.domain.CardTypeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountResourceDTO;
import uk.gov.pay.connector.model.domain.NotificationCredentials;

import java.io.IOException;
import java.util.*;

import static java.util.Collections.emptyMap;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;

public class GatewayAccountDaoITest extends DaoITestBase {

    private GatewayAccountDao gatewayAccountDao;
    private DatabaseFixtures databaseFixtures;

    @Before
    public void setUp() throws Exception {
        gatewayAccountDao = env.getInstance(GatewayAccountDao.class);
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);
    }

    @Test
    public void persist_shouldCreateAnAccount() throws Exception {
        DatabaseFixtures.TestCardType mastercardCreditCardTypeRecord = createMastercardCreditCardTypeRecord();
        DatabaseFixtures.TestCardType visaDebitCardTypeRecord = createVisaDebitCardTypeRecord();
        createAccountRecord(mastercardCreditCardTypeRecord, visaDebitCardTypeRecord);

        String paymentProvider = "test provider";
        GatewayAccountEntity account = new GatewayAccountEntity(paymentProvider, new HashMap<>(), TEST);

        CardTypeEntity masterCardCreditCardType = new CardTypeEntity();
        masterCardCreditCardType.setId(mastercardCreditCardTypeRecord.getId());

        CardTypeEntity visaCardDebitCardType = new CardTypeEntity();
        visaCardDebitCardType.setId(visaDebitCardTypeRecord.getId());

        account.setCardTypes(Arrays.asList(masterCardCreditCardType, visaCardDebitCardType));

        gatewayAccountDao.persist(account);

        assertThat(account.getId(), is(notNullValue()));
        assertThat(account.getEmailNotification(), is(notNullValue()));
        assertThat(account.getDescription(), is(nullValue()));
        assertThat(account.getAnalyticsId(), is(nullValue()));
        assertThat(account.getEmailNotification().getAccountEntity().getId(), is(account.getId()));
        assertThat(account.getEmailNotification().isEnabled(), is(true));
        assertThat(account.getNotificationCredentials(), is(nullValue()));

        databaseTestHelper.getAccountCredentials(account.getId());

        List<Map<String, Object>> acceptedCardTypesByAccountId = databaseTestHelper.getAcceptedCardTypesByAccountId(account.getId());

        assertThat(acceptedCardTypesByAccountId, containsInAnyOrder(
                allOf(
                        org.hamcrest.Matchers.hasEntry("label", mastercardCreditCardTypeRecord.getLabel()),
                        org.hamcrest.Matchers.hasEntry("type", mastercardCreditCardTypeRecord.getType().toString()),
                        org.hamcrest.Matchers.hasEntry("brand", mastercardCreditCardTypeRecord.getBrand())
                ), allOf(
                        org.hamcrest.Matchers.hasEntry("label", visaDebitCardTypeRecord.getLabel()),
                        org.hamcrest.Matchers.hasEntry("type", visaDebitCardTypeRecord.getType().toString()),
                        org.hamcrest.Matchers.hasEntry("brand", visaDebitCardTypeRecord.getBrand())
                )));
    }

    @Test
    public void findById_shouldNotFindANonexistentGatewayAccount() throws Exception {
        assertThat(gatewayAccountDao.findById(GatewayAccountEntity.class, 1234L).isPresent(), is(false));
    }

    @Test
    public void findById_shouldFindGatewayAccount() throws Exception {
        DatabaseFixtures.TestCardType mastercardCreditCardTypeRecord = createMastercardCreditCardTypeRecord();
        DatabaseFixtures.TestCardType visaDebitCardTypeRecord = createVisaDebitCardTypeRecord();
        DatabaseFixtures.TestAccount accountRecord = createAccountRecord(mastercardCreditCardTypeRecord, visaDebitCardTypeRecord);

        Optional<GatewayAccountEntity> gatewayAccountOpt =
                gatewayAccountDao.findById(GatewayAccountEntity.class, accountRecord.getAccountId());

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(accountRecord.getPaymentProvider()));
        Map<String, String> credentialsMap = gatewayAccount.getCredentials();
        assertThat(credentialsMap.size(), is(0));
        assertThat(gatewayAccount.getServiceName(), is(accountRecord.getServiceName()));
        assertThat(gatewayAccount.getDescription(), is(accountRecord.getDescription()));
        assertThat(gatewayAccount.getAnalyticsId(), is(accountRecord.getAnalyticsId()));
        assertThat(gatewayAccount.getCardTypes(), contains(
                allOf(
                        hasProperty("id", is(Matchers.notNullValue())),
                        hasProperty("label", is(mastercardCreditCardTypeRecord.getLabel())),
                        hasProperty("type", is(mastercardCreditCardTypeRecord.getType())),
                        hasProperty("brand", is(mastercardCreditCardTypeRecord.getBrand()))
                ), allOf(
                        hasProperty("id", is(Matchers.notNullValue())),
                        hasProperty("label", is(visaDebitCardTypeRecord.getLabel())),
                        hasProperty("type", is(visaDebitCardTypeRecord.getType())),
                        hasProperty("brand", is(visaDebitCardTypeRecord.getBrand()))
                )));
    }

    @Test
    public void findById_shouldUpdateAccountCardTypes() throws Exception {
        DatabaseFixtures.TestCardType mastercardCreditCardTypeRecord = createMastercardCreditCardTypeRecord();
        DatabaseFixtures.TestCardType visaCreditCardTypeRecord = createVisaCreditCardTypeRecord();
        DatabaseFixtures.TestCardType visaDebitCardTypeRecord = createVisaDebitCardTypeRecord();
        DatabaseFixtures.TestAccount accountRecord = createAccountRecord(mastercardCreditCardTypeRecord, visaCreditCardTypeRecord);

        Optional<GatewayAccountEntity> gatewayAccountOpt =
                gatewayAccountDao.findById(GatewayAccountEntity.class, accountRecord.getAccountId());

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();

        CardTypeEntity visaDebitCardType = new CardTypeEntity();
        visaDebitCardType.setId(visaDebitCardTypeRecord.getId());

        List<CardTypeEntity> cardTypes = gatewayAccount.getCardTypes();

        cardTypes.removeIf(p -> p.getId().equals(visaCreditCardTypeRecord.getId()));
        cardTypes.add(visaDebitCardType);

        gatewayAccountDao.merge(gatewayAccount);

        List<Map<String, Object>> acceptedCardTypesByAccountId = databaseTestHelper.getAcceptedCardTypesByAccountId(accountRecord.getAccountId());

        assertThat(acceptedCardTypesByAccountId, contains(
                allOf(
                        org.hamcrest.Matchers.hasEntry("label", mastercardCreditCardTypeRecord.getLabel()),
                        org.hamcrest.Matchers.hasEntry("type", mastercardCreditCardTypeRecord.getType().toString()),
                        org.hamcrest.Matchers.hasEntry("brand", mastercardCreditCardTypeRecord.getBrand())
                ), allOf(
                        org.hamcrest.Matchers.hasEntry("label", visaDebitCardTypeRecord.getLabel()),
                        org.hamcrest.Matchers.hasEntry("type", visaDebitCardTypeRecord.getType().toString()),
                        org.hamcrest.Matchers.hasEntry("brand", visaDebitCardTypeRecord.getBrand())
                )));
    }

    @Test
    public void findById_shouldUpdateEmptyCredentials() throws IOException {
        String paymentProvider = "test provider";
        Long accountId = 888L;
        databaseTestHelper.addGatewayAccount(accountId.toString(), paymentProvider);

        GatewayAccountEntity gatewayAccount = gatewayAccountDao.findById(accountId).get();

        assertThat(gatewayAccount.getCredentials(), is(emptyMap()));

        gatewayAccount.setCredentials(new HashMap<String, String>() {{
            put("username", "Username");
            put("password", "Password");
        }});

        gatewayAccountDao.merge(gatewayAccount);

        Optional<GatewayAccountEntity> serviceAccountMaybe = gatewayAccountDao.findById(accountId);
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", "Username"));
        assertThat(credentialsMap, hasEntry("password", "Password"));
    }

    @Test
    public void findById_shouldUpdateAndRetrieveCredentialsWithSpecialCharacters() throws Exception {
        String paymentProvider = "test provider";
        String accountId = "333";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);

        String aUserNameWithSpecialChars = "someone@some{[]where&^%>?\\/";
        String aPasswordWithSpecialChars = "56g%%Bqv\\>/<wdUpi@#bh{[}]6JV+8w";
        ImmutableMap<String, String> credMap = ImmutableMap.of("username", aUserNameWithSpecialChars, "password", aPasswordWithSpecialChars);

        GatewayAccountEntity gatewayAccount = gatewayAccountDao.findById(Long.valueOf(accountId)).get();
        gatewayAccount.setCredentials(credMap);

        gatewayAccountDao.merge(gatewayAccount);

        Optional<GatewayAccountEntity> serviceAccountMaybe = gatewayAccountDao.findById(Long.valueOf(accountId));
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", aUserNameWithSpecialChars));
        assertThat(credentialsMap, hasEntry("password", aPasswordWithSpecialChars));
    }

    @Test
    public void findById_shouldFindAccountInfoByIdWhenFindingByIdReturningGatewayAccount() throws Exception {
        String paymentProvider = "test provider";
        String accountId = "12345";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);

        Optional<GatewayAccountEntity> gatewayAccountOpt = gatewayAccountDao.findById(Long.valueOf(accountId));

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(paymentProvider));
        assertThat(gatewayAccount.getCredentials().size(), is(0));
    }

    @Test
    public void findById_shouldGetCredentialsWhenFindingGatewayAccountById() {
        String paymentProvider = "test provider";
        String accountId = "786";
        HashMap<String, String> credentials = new HashMap<>();
        credentials.put("username", "Username");
        credentials.put("password", "Password");

        databaseTestHelper.addGatewayAccount(accountId, paymentProvider, credentials);

        Optional<GatewayAccountEntity> gatewayAccount = gatewayAccountDao.findById(Long.valueOf(accountId));

        assertThat(gatewayAccount.isPresent(), is(true));
        Map<String, String> accountCredentials = gatewayAccount.get().getCredentials();
        assertThat(accountCredentials, hasEntry("username", "Username"));
        assertThat(accountCredentials, hasEntry("password", "Password"));
    }

    @Test
    public void shouldSaveNotificationCredentials() {
        String paymentProvider = "test provider";
        String accountId = "88888";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);

        GatewayAccountEntity gatewayAccount = gatewayAccountDao.findById(Long.valueOf(accountId)).get();

        NotificationCredentials notificationCredentials = new NotificationCredentials(gatewayAccount);
        notificationCredentials.setPassword("password");
        notificationCredentials.setUserName("username");
        gatewayAccount.setNotificationCredentials(notificationCredentials);

        gatewayAccountDao.merge(gatewayAccount);

        GatewayAccountEntity retrievedGatewayAccount = gatewayAccountDao.findById(Long.valueOf(accountId)).get();

        assertNotNull(retrievedGatewayAccount.getNotificationCredentials());
        assertThat(retrievedGatewayAccount.getNotificationCredentials().getUserName(), is("username"));
        assertThat(retrievedGatewayAccount.getNotificationCredentials().getPassword(), is("password"));
    }

    @Test
    public void shouldListAllAccounts() throws Exception {
        databaseTestHelper.addGatewayAccount("123", "provider-1", ImmutableMap.of("user", "fuser", "password", "word"), "service-name-1", TEST, "description-1", "analytics-id-1");
        databaseTestHelper.addGatewayAccount("456", "provider-2", null, "service-name-2", TEST, "description-2", "analytics-id-2");
        databaseTestHelper.addGatewayAccount("789", "provider-3", null, "service-name-3", GatewayAccountEntity.Type.LIVE, "description-3", "analytics-id-3");

        List<GatewayAccountResourceDTO> gatewayAccounts = gatewayAccountDao.listAll();

        assertEquals(3, gatewayAccounts.size());
        assertThat(gatewayAccounts.get(0).getAccountId(), is(123L));
        assertEquals("provider-1", gatewayAccounts.get(0).getPaymentProvider());
        assertEquals("description-1", gatewayAccounts.get(0).getDescription());
        assertEquals("service-name-1", gatewayAccounts.get(0).getServiceName());
        assertEquals(TEST.toString(), gatewayAccounts.get(0).getType());
        assertEquals("analytics-id-1", gatewayAccounts.get(0).getAnalyticsId());

        assertEquals("provider-2", gatewayAccounts.get(1).getPaymentProvider());
        assertEquals("provider-3", gatewayAccounts.get(2).getPaymentProvider());
        assertThat(gatewayAccounts.get(1).getAccountId(), is(456L));
        assertThat(gatewayAccounts.get(2).getAccountId(), is(789L));
    }

    private DatabaseFixtures.TestCardType createMastercardCreditCardTypeRecord() {
        return databaseFixtures.aMastercardCreditCardType().insert();
    }

    private DatabaseFixtures.TestCardType createVisaDebitCardTypeRecord() {
        return databaseFixtures.aVisaDebitCardType().insert();
    }

    private DatabaseFixtures.TestCardType createVisaCreditCardTypeRecord() {
        return databaseFixtures.aVisaCreditCardType().insert();
    }

    private DatabaseFixtures.TestAccount createAccountRecord(DatabaseFixtures.TestCardType... cardTypes) {
        return databaseFixtures
                .aTestAccount()
                .withCardTypes(Arrays.asList(cardTypes))
                .insert();
    }
}
