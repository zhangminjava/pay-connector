package uk.gov.pay.connector.model.domain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.resources.PaymentGatewayName;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

abstract class StateTransitions {
    private Map<ChargeStatus, List<ChargeStatus>> transitionTable;

    StateTransitions(Map<ChargeStatus, List<ChargeStatus>> transitionTable) {
        this.transitionTable = transitionTable;
    }

    boolean isValidTransition(ChargeStatus state, ChargeStatus targetState) {
        return transitionTable.getOrDefault(state, emptyList()).contains(targetState);
    }

    static ImmutableList<ChargeStatus> validTransitions(ChargeStatus... statuses) {
        return ImmutableList.copyOf(statuses);
    }
}



