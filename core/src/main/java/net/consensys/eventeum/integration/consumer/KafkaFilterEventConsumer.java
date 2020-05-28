/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.consensys.eventeum.integration.consumer;

import net.consensys.eventeum.ContractsRepository;
import net.consensys.eventeum.TransactionDetailsRepository;
import net.consensys.eventeum.dto.block.BlockDetails;
import net.consensys.eventeum.dto.event.ContractEventDetails;
import net.consensys.eventeum.dto.message.*;
import net.consensys.eventeum.dto.transaction.TransactionDetails;
import net.consensys.eventeum.integration.KafkaSettings;
import net.consensys.eventeum.integration.broadcast.BroadcastException;
import net.consensys.eventeum.integration.consumer.model.WalletNotifyBody;
import net.consensys.eventeum.integration.consumer.model.Webhook;
import net.consensys.eventeum.integration.eventstore.db.repository.ContractEventDetailsRepository;
import net.consensys.eventeum.repository.TransactionMonitoringSpecRepository;
import net.consensys.eventeum.service.SubscriptionService;
import net.consensys.eventeum.service.TransactionMonitoringService;
import net.consensys.eventeum.utils.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A FilterEventConsumer that consumes ContractFilterEvents messages from a Kafka topic.
 * <p>
 * The topic to be consumed from can be configured via the kafka.topic.contractEvents property.
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
@Component
public class KafkaFilterEventConsumer implements EventeumInternalEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaFilterEventConsumer.class);

    private final Map<String, Consumer<EventeumMessage>> messageConsumers;

    @Value("${pusher.url}")
    private String pusherBaseUrl;

    @Value("${cws.hostname}")
    private String hostname;

    @Value("${cws.coin}")
    private String coin;

    private RestTemplate restTemplate;

    private RetryTemplate retryTemplate;

    @Autowired
    public KafkaFilterEventConsumer(SubscriptionService subscriptionService,
                                    TransactionMonitoringService transactionMonitoringService,
                                    KafkaSettings kafkaSettings,
                                    ContractEventDetailsRepository contractEventDetailsRepository,
                                    ContractsRepository contractsRepository,
                                    TransactionDetailsRepository transactionDetailsRepository,
                                    TransactionMonitoringSpecRepository transactionMonitoringSpecRepository,
                                    @Qualifier("cwsRetryTemplate") RetryTemplate retryTemple) {

        messageConsumers = new HashMap<>();
        restTemplate = new RestTemplate();
        this.retryTemplate = retryTemple;

        messageConsumers.put(BlockEvent.TYPE, (message) -> {
            BlockDetails details = ((BlockEvent) message).getDetails();
            sendBlockToEndpoint(details.getNumber().toString());
        });

        messageConsumers.put(TransactionEvent.TYPE, (message) -> {
            TransactionDetails details = (TransactionDetails) message.getDetails();
//            sendTxToEndpoint(details.getHash(), details.getContractAddress(),
//                    new BigInteger(details.getBlockNumber().substring(2), 16), hostname, coin);

        });

        messageConsumers.put(ContractEvent.TYPE, (message) -> {
            ContractEventDetails details = (ContractEventDetails) message.getDetails();
//            sendContractEventToEndpoint(details);
        });

        messageConsumers.put(WebhookMessage.TYPE, (message) -> {
            Webhook details = (Webhook) message.getDetails();
            sendWebhookMessage(details);
        });
    }

    @Override
    @KafkaListener(topics = {"contract-events", "transaction-events", "block-events"}, groupId = "#{eventeumKafkaSettings.groupId}",
            containerFactory = "eventeumKafkaListenerContainerFactory")
    public void onMessage(EventeumMessage message) {
        final Consumer<EventeumMessage> consumer = messageConsumers.get(message.getType());

        if (consumer == null) {
            logger.error(String.format("No consumer for message type %s!", message.getType()));
            return;
        }
        consumer.accept(message);
    }

    public void sendWebhookMessage(Webhook webhook) {

        WalletNotifyBody walletNotifyBody = new WalletNotifyBody(
                webhook.getTxid(),
                webhook.getAddresses(),
                webhook.getBlockHeight()
        );

        final String txEndpoint = pusherBaseUrl + "/" + hostname + "/node/wallet-notify/" + webhook.getCoin();

        try {
            retryTemplate.execute(retryContext -> {
                logger.info("Sending Transaction event to -> " + pusherBaseUrl + "/" + hostname + "/node/wallet-notify/" + webhook.getCoin() + " \n" +
                        "With Body" + JSON.stringify(walletNotifyBody));
                final ResponseEntity<Void> response = restTemplate.postForEntity(txEndpoint,
                        walletNotifyBody, Void.class);

                checkForSuccessResponse(response);
                return null;
            });
        } catch (Exception ignored) {

        }
    }

    public void sendBlockToEndpoint(String blockNumber) {
        try {
            retryTemplate.execute(retryContext -> {
                logger.info("Sending Block event to -> " + pusherBaseUrl + "/" + hostname + "/node/block-notify/" + coin + "/" + blockNumber);
                final String blockEndpoint = pusherBaseUrl + "/" + hostname + "/node/block-notify/" + coin + "/" + blockNumber;
                ResponseEntity<Void> response = restTemplate.postForEntity(blockEndpoint, null, Void.class);
                checkForSuccessResponse(response);
                return null;
            });
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

    }

    private void checkForSuccessResponse(ResponseEntity response) {
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new BroadcastException(
                    String.format("Received a %s response when broadcasting via http", response.getStatusCode()));
        }
    }


}