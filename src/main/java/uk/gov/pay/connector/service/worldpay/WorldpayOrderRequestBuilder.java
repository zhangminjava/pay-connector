package uk.gov.pay.connector.service.worldpay;

import javax.ws.rs.core.MediaType;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import uk.gov.pay.connector.model.OrderRequestType;
import uk.gov.pay.connector.service.OrderRequestBuilder;
import uk.gov.pay.connector.util.templates.PayloadBuilder;
import uk.gov.pay.connector.util.templates.TemplateBuilder;

public class WorldpayOrderRequestBuilder extends OrderRequestBuilder {
    Logger logger = Logger.getLogger(WorldpayOrderRequestBuilder.class);

    static public class WorldpayTemplateData extends TemplateData {
        private String reference;
        private String amount;
        private DateTime captureDate;
        private String sessionId;
        private String acceptHeader;
        private String userAgentHeader;
        private boolean requires3ds;
        private String paResponse3ds;

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        @Override
        public String getAmount() {
            return amount;
        }

        @Override
        public void setAmount(String amount) {
            this.amount = amount;
        }

        public DateTime getCaptureDate() {
            return captureDate;
        }

        public void setCaptureDate(DateTime captureDate) {
            this.captureDate = captureDate;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getAcceptHeader() {
            return acceptHeader;
        }

        public void setAcceptHeader(String acceptHeader) {
            this.acceptHeader = acceptHeader;
        }

        public String getUserAgentHeader() {
            return userAgentHeader;
        }

        public void setUserAgentHeader(String userAgentHeader) {
            this.userAgentHeader = userAgentHeader;
        }

        public boolean isRequires3ds() {
            return requires3ds;
        }

        public void setRequires3ds(boolean requires3ds) {
            this.requires3ds = requires3ds;
        }

        public String getPaResponse3ds() {
            return paResponse3ds;
        }

        public void setPaResponse3ds(String paResponse3ds) {
            this.paResponse3ds = paResponse3ds;
        }
    }

    public static final TemplateBuilder AUTHORISE_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayAuthoriseOrderTemplate.xml");
    public static final TemplateBuilder AUTH_3DS_RESPONSE_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/Worldpay3dsResponseAuthOrderTemplate.xml");
    public static final TemplateBuilder CAPTURE_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayCaptureOrderTemplate.xml");
    public static final TemplateBuilder CANCEL_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayCancelOrderTemplate.xml");
    public static final TemplateBuilder REFUND_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayRefundOrderTemplate.xml");

    private final WorldpayTemplateData worldpayTemplateData;

    public static WorldpayOrderRequestBuilder aWorldpayAuthoriseOrderRequestBuilder() {
        return new WorldpayOrderRequestBuilder(new WorldpayTemplateData(), AUTHORISE_ORDER_TEMPLATE_BUILDER, OrderRequestType.AUTHORISE);
    }

    public static WorldpayOrderRequestBuilder aWorldpay3dsResponseAuthOrderRequestBuilder() {
        return new WorldpayOrderRequestBuilder(new WorldpayTemplateData(), AUTH_3DS_RESPONSE_ORDER_TEMPLATE_BUILDER, OrderRequestType.AUTHORISE_3DS);
    }

    public static WorldpayOrderRequestBuilder aWorldpayCaptureOrderRequestBuilder() {
        return new WorldpayOrderRequestBuilder(new WorldpayTemplateData(), CAPTURE_ORDER_TEMPLATE_BUILDER, OrderRequestType.CAPTURE);
    }

    public static WorldpayOrderRequestBuilder aWorldpayCancelOrderRequestBuilder() {
        return new WorldpayOrderRequestBuilder(new WorldpayTemplateData(), CANCEL_ORDER_TEMPLATE_BUILDER, OrderRequestType.CANCEL);
    }

    public static WorldpayOrderRequestBuilder aWorldpayRefundOrderRequestBuilder() {
        return new WorldpayOrderRequestBuilder(new WorldpayTemplateData(), REFUND_ORDER_TEMPLATE_BUILDER, OrderRequestType.REFUND);
    }

    private WorldpayOrderRequestBuilder(WorldpayTemplateData worldpayTemplateData, PayloadBuilder payloadBuilder, OrderRequestType orderRequestType) {
        super(worldpayTemplateData, payloadBuilder, orderRequestType);
        this.worldpayTemplateData = worldpayTemplateData;
    }

    public WorldpayOrderRequestBuilder withReference(String reference) {
        worldpayTemplateData.setReference(reference);
        return this;
    }

    public WorldpayOrderRequestBuilder withAmount(String amount) {
        worldpayTemplateData.setAmount(amount);
        return this;
    }

    public WorldpayOrderRequestBuilder withDate(DateTime date) {
        worldpayTemplateData.setCaptureDate(date);
        return this;
    }

    public WorldpayOrderRequestBuilder withSessionId(String sessionId) {
        worldpayTemplateData.setSessionId(sessionId);
        return this;
    }

    public WorldpayOrderRequestBuilder withAcceptHeader(String acceptHeader) {
        worldpayTemplateData.setAcceptHeader(acceptHeader);
        return this;
    }

    public WorldpayOrderRequestBuilder withUserAgentHeader(String userAgentHeader) {
        worldpayTemplateData.setUserAgentHeader(userAgentHeader);
        return this;
    }

    public WorldpayOrderRequestBuilder with3dsRequired(boolean requires3ds) {
        logger.info("3DS requirement is: "+ requires3ds +" for "+ worldpayTemplateData.sessionId);
        worldpayTemplateData.setRequires3ds(requires3ds);
        return this;
    }

    public WorldpayOrderRequestBuilder withPaResponse3ds(String paResponse) {
        worldpayTemplateData.setPaResponse3ds(paResponse);
        return this;
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.APPLICATION_XML_TYPE;
    }
}
