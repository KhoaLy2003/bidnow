package com.bidnow.wallet.kafka;

import com.bidnow.common.dto.event.PaymentEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/** In-process carrier for a PaymentEvent, published to Kafka only after the DB transaction commits. */
@Getter
public class PaymentApplicationEvent extends ApplicationEvent {

    private final PaymentEvent payment;

    public PaymentApplicationEvent(Object source, PaymentEvent payment) {
        super(source);
        this.payment = payment;
    }
}
