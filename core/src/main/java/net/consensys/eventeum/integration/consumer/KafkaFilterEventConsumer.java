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

import java.math.BigInteger;
import java.util.Collections;
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
            sendTxToEndpoint(details.getHash(), details.getContractAddress(),
                    new BigInteger(details.getBlockNumber().substring(2), 16), "cwsprod", "eth");

        });

        messageConsumers.put(ContractEvent.TYPE, (message) -> {
            ContractEventDetails details = (ContractEventDetails) message.getDetails();
            sendContractEventToEndpoint(details);
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

        final String txEndpoint = pusherBaseUrl + "/{hostname}/node/wallet-notify/{coin}";

        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("hostname", webhook.getHostname());
        pathParams.put("coin", webhook.getCoin());
        try {
            retryTemplate.execute(retryContext -> {
                final ResponseEntity<Void> response = restTemplate.postForEntity(txEndpoint,
                        walletNotifyBody, Void.class, pathParams);

                checkForSuccessResponse(response);
                return null;
            });
        } catch (Exception ignored) {

        }
    }

    public void sendBlockToEndpoint(String blockNumber) {
        try {
            retryTemplate.execute(retryContext -> {
                final String blockEndpoint = pusherBaseUrl + "/cwsprod/node/block-notify/eth/" + blockNumber;
                ResponseEntity<Void> response = restTemplate.postForEntity(blockEndpoint, null, Void.class);
                checkForSuccessResponse(response);
                return null;
            });
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

    }

    public void sendTxToEndpoint(String txHash, String address, BigInteger blockNumber, String hostname, String coin) {
        WalletNotifyBody walletNotifyBody = new WalletNotifyBody(
                txHash,
                Collections.singletonList(address.toLowerCase()),
                blockNumber
        );

        final String txEndpoint = pusherBaseUrl + "/{hostname}/node/wallet-notify/{coin}";

        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("hostname", hostname);
        pathParams.put("coin", coin);
        try {
            retryTemplate.execute(retryContext -> {
                final ResponseEntity<Void> response = restTemplate.postForEntity(txEndpoint,
                        walletNotifyBody, Void.class, pathParams);

                checkForSuccessResponse(response);
                return null;
            });
        } catch (Exception ignored) {

        }

    }

    public void sendContractEventToEndpoint(ContractEventDetails details) {

        WalletNotifyBody walletNotifyBody;

        final String txEndpoint = pusherBaseUrl + "/{hostname}/node/wallet-notify/{coin}";

        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("hostname", details.getHostname());
        pathParams.put("coin", details.getCoin());

        if (details.getWalletContractAddress() == null) {
            walletNotifyBody = new WalletNotifyBody(details.getTransactionHash(),
                    Collections.singletonList(details.getAddress().toLowerCase()),
                    details.getBlockNumber());
        } else {
            walletNotifyBody = new WalletNotifyBody(details.getTransactionHash(),
                    Collections.singletonList(details.getWalletContractAddress().toLowerCase()),
                    details.getBlockNumber());
        }
        try {
            retryTemplate.execute(retryContext -> {
                final ResponseEntity<Void> response = restTemplate.postForEntity(txEndpoint,
                        walletNotifyBody, Void.class, pathParams);

                checkForSuccessResponse(response);
                return null;
            });
        } catch (Exception ignored) {

        }

    }

    private void checkForSuccessResponse(ResponseEntity response) {
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new BroadcastException(
                    String.format("Received a %s response when broadcasting via http", response.getStatusCode()));
        }
    }


}