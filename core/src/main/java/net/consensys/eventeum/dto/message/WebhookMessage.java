package net.consensys.eventeum.dto.message;

import lombok.NoArgsConstructor;
import net.consensys.eventeum.integration.consumer.model.Webhook;

@NoArgsConstructor
public class WebhookMessage extends AbstractMessage<Webhook> {

    public static final String TYPE = "WebhookMessage";

    public WebhookMessage(Webhook details) {
        super(details.getTxid(), TYPE, details);
    }

}
