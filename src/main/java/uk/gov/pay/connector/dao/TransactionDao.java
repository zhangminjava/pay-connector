package uk.gov.pay.connector.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import uk.gov.pay.connector.model.TransactionDto;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static jersey.repackaged.com.google.common.collect.Lists.newArrayList;

public class TransactionDao {

    private final Provider<EntityManager> entityManager;
    private final UTCDateTimeConverter utcDateTimeConverter;

    @Inject
    public TransactionDao(Provider<EntityManager> entityManager, UTCDateTimeConverter utcDateTimeConverter) {
        this.entityManager = entityManager;
        this.utcDateTimeConverter = utcDateTimeConverter;
    }

    public List<TransactionDto> findAllBy(ChargeSearchParams params) {

        String query = "SELECT * FROM" +
                "((SELECT 'charge' as transaction_type, charge.external_id, charge.reference, charge.description, charge.status, charge.email,  charge.gateway_account_id, charge.gateway_transaction_id, charge.created_date as date_created, charge.card_brand, charge.cardholder_name, charge.expiry_date, charge.last_digits_card_number, charge.address_city, charge.address_country, charge.address_county, charge.address_line1, charge.address_line2, charge.address_postcode, charge.amount " +
                "FROM charges AS charge) UNION " +
                "(SELECT 'refund' as transaction_type, rcharge.external_id, rcharge.reference, rcharge.description, refund.status, rcharge.email, rcharge.gateway_account_id, rcharge.gateway_transaction_id, refund.created_date as date_created, rcharge.card_brand, rcharge.cardholder_name, rcharge.expiry_date, rcharge.last_digits_card_number, rcharge.address_city, rcharge.address_country, rcharge.address_county, rcharge.address_line1, rcharge.address_line2, rcharge.address_postcode, refund.amount " +
                "FROM refunds AS refund, charges AS rcharge WHERE rcharge.id=refund.charge_id)) AS car ORDER BY car.date_created DESC";

        List<Object[]> resultList = entityManager.get().createNativeQuery(query).getResultList();
        List<TransactionDto> transactions = newArrayList();

        for (Object[] o : resultList) {
            transactions.add(new TransactionDto((String) o[0], (String) o[1], (String) o[2], (String) o[3], (String) o[4], (String) o[5], (Long) o[6], (String) o[7], ZonedDateTime.ofInstant(((Timestamp) o[8]).toInstant(), ZoneId.of("UTC")), (String) o[9], (String) o[10], (String) o[11], (String) o[12], (String) o[13], (String) o[14], (String) o[15], (String) o[16], (String) o[17], (String) o[18], (Long) o[19]));
        }

        return transactions;
    }

}
