package uk.gov.pay.connector.resources;

import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.AuthCardDetails;

import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class AuthCardDetailsValidator {

    public static boolean isWellFormatted(AuthCardDetails authCardDetails) {
        return isValidCardNumberLength(authCardDetails.getCardNo()) &&
                isBetween3To4Digits(authCardDetails.getCvc()) &&
                hasExpiryDateFormat(authCardDetails.getEndDate()) &&
                hasAddress(authCardDetails.getAddress()) &&
                hasCardBrand(authCardDetails.getCardBrand());
    }

    private static boolean hasAddress(Address address) {
        return address != null &&
                isNotBlank(address.getCity()) &&
                isNotBlank(address.getLine1()) &&
                isNotBlank(address.getPostcode()) &&
                isNotBlank(address.getCountry());
    }

    private static boolean hasExpiryDateFormat(Object date) {
        return date != null && date.toString().matches("[0-9]{2}/[0-9]{2}");
    }

    private static boolean hasCardBrand(String cardBrand) {
        return isNoneBlank(cardBrand);
    }

    private static boolean isValidCardNumberLength(Object number) {
        return number != null && number.toString().matches("[0-9]{12,19}");
    }

    private static boolean isBetween3To4Digits(Object number) {
        return number != null && number.toString().matches("[0-9]{3,4}");
    }
}
