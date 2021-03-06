package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.time.ZonedDateTime;
import java.util.Optional;

@Entity
@Table(name = "charge_events")
@SequenceGenerator(name = "charge_events_charge_id_seq", sequenceName = "charge_events_charge_id_seq", allocationSize = 1)
public class ChargeEventEntity extends AbstractEntity {

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "charge_id", updatable = false)
    private ChargeEntity chargeEntity;

    @Convert(converter = ChargeStatusConverter.class)
    private ChargeStatus status;

    @Column(name = "gateway_event_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime gatewayEventDate;

    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime updated;

    protected ChargeEventEntity() {
    }

    public ChargeEventEntity(ChargeEntity chargeEntity, ChargeStatus chargeStatus, ZonedDateTime updated, Optional<ZonedDateTime> gatewayEventDate) {
        this.chargeEntity = chargeEntity;
        this.status = chargeStatus;
        this.gatewayEventDate = gatewayEventDate.orElse(null);
        this.updated = updated;
    }

    public ChargeStatus getStatus() {
        return status;
    }

    public Optional<ZonedDateTime> getGatewayEventDate() {
        return Optional.ofNullable(gatewayEventDate);
    }

    public ZonedDateTime getUpdated() {
        return updated;
    }

    public ChargeEntity getChargeEntity() {
        return chargeEntity;
    }

    public static ChargeEventEntity from(ChargeEntity chargeEntity, ChargeStatus chargeStatus, ZonedDateTime updated, Optional<ZonedDateTime> gatewayEventDate) {
        return new ChargeEventEntity(chargeEntity, chargeStatus, updated, gatewayEventDate);
    }
}
