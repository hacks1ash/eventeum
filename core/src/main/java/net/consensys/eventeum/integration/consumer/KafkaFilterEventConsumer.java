package net.consensys.eventeum.integration.consumer;

import net.consensys.eventeum.ContractsRepository;
import net.consensys.eventeum.TransactionDetailsRepository;
import net.consensys.eventeum.WalletContract;
import net.consensys.eventeum.dto.block.BlockDetails;
import net.consensys.eventeum.dto.event.ContractEventDetails;
import net.consensys.eventeum.dto.message.BlockEvent;
import net.consensys.eventeum.dto.message.ContractEvent;
import net.consensys.eventeum.dto.message.EventeumMessage;
import net.consensys.eventeum.dto.message.TransactionEvent;
import net.consensys.eventeum.dto.transaction.TransactionDetails;
import net.consensys.eventeum.integration.KafkaSettings;
import net.consensys.eventeum.integration.consumer.model.WalletNotifyBody;
import net.consensys.eventeum.integration.eventstore.db.repository.ContractEventDetailsRepository;
import net.consensys.eventeum.model.TransactionMonitoringSpec;
import net.consensys.eventeum.repository.TransactionMonitoringSpecRepository;
import net.consensys.eventeum.service.SubscriptionService;
import net.consensys.eventeum.service.TransactionMonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.ReadonlyTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A FilterEventConsumer that consumes ContractFilterEvents messages from a Kafka topic.
 * <p>
 * The topic to be consumed from can be configured via the kafka.topic.contractEvents property.
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
// TODO add connector-pusher url in application.properties
@Component
public class KafkaFilterEventConsumer implements EventeumInternalEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaFilterEventConsumer.class);

    private final Map<String, Consumer<EventeumMessage>> messageConsumers;

    @Value("${contracts.database.token.ID}")
    private String tokenID;

    @Value("${contracts.database.wallet.ID}")
    private String walletID;

    @Value("${ether.node.url}")
    private String web3URL;

    private PusherAPI pusherAPI = PusherMiddleware.getPusherApi();

    @Autowired
    public KafkaFilterEventConsumer(SubscriptionService subscriptionService,
                                    TransactionMonitoringService transactionMonitoringService,
                                    KafkaSettings kafkaSettings,
                                    ContractEventDetailsRepository contractEventDetailsRepository,
                                    ContractsRepository contractsRepository,
                                    TransactionDetailsRepository transactionDetailsRepository,
                                    TransactionMonitoringSpecRepository transactionMonitoringSpecRepository) {

        messageConsumers = new HashMap<>();
//        messageConsumers.put(ContractEventFilterAdded.TYPE, (message) -> {
//            subscriptionService.registerContractEventFilter(
//                    (ContractEventFilter) message.getDetails(), false);
//        });
//
//        messageConsumers.put(ContractEventFilterRemoved.TYPE, (message) -> {
//            try {
//                subscriptionService.unregisterContractEventFilter(
//                        ((ContractEventFilter) message.getDetails()).getId(), false);
//            } catch (NotFoundException e) {
//                logger.debug("Received filter removed message but filter doesn't exist. (We probably sent message)");
//            }
//        });
//
//        messageConsumers.put(TransactionMonitorAdded.TYPE, (message) -> {
//            transactionMonitoringService.registerTransactionsToMonitor(
//                    (TransactionMonitoringSpec) message.getDetails(), false);
//        });
//
//        messageConsumers.put(TransactionMonitorRemoved.TYPE, (message) -> {
//            try {
//                transactionMonitoringService.stopMonitoringTransactions(
//                        ((TransactionMonitoringSpec) message.getDetails()).getId(), false);
//            } catch (NotFoundException e) {
//                logger.debug("Received transaction monitor removed message but monitor doesn't exist. (We probably sent message)");
//            }
//        });

        messageConsumers.put(BlockEvent.TYPE, (message) -> {
            BlockDetails details = ((BlockEvent) message).getDetails();
            pusherAPI.sendBlockNotify("test", "teth", details.getNumber());
        });

        messageConsumers.put(TransactionEvent.TYPE, (message) -> {
            // TODO find solution for internal transfers
            TransactionDetails details = (TransactionDetails) message.getDetails();
            transactionDetailsRepository.save(details);
            String to = details.getTo();
            String from = details.getFrom();

            List<TransactionMonitoringSpec> toSpec = transactionMonitoringSpecRepository.findAllByTransactionIdentifierValue(to);
            List<TransactionMonitoringSpec> fromSpec = transactionMonitoringSpecRepository.findAllByTransactionIdentifierValue(from);
            TransactionMonitoringSpec transactionMonitoringSpec = null;

            if (toSpec.size() > 0) {
                transactionMonitoringSpec = toSpec.get(0);
            } else if (fromSpec.size() > 0) {
                transactionMonitoringSpec = fromSpec.get(0);
            }

            pusherAPI.sendWalletNotify(transactionMonitoringSpec.getHostname(), transactionMonitoringSpec.getCoin(),
                    new WalletNotifyBody(
                            details.getHash(),
                            Collections.singletonList(transactionMonitoringSpec.getTransactionIdentifierValue()),
                            new BigInteger(details.getBlockNumber()))
            );
        });

        messageConsumers.put(ContractEvent.TYPE, (message) -> {

            Web3j web3j = Web3j.build(new HttpService(web3URL));
            ContractEventDetails details = (ContractEventDetails) message.getDetails();

            contractsRepository.findById(walletID).ifPresent(wallet -> {
                if (wallet.getContractAddresses().contains(details.getAddress())) {
                    contractEventDetailsRepository.save(details);
                    pusherAPI.sendWalletNotify(details.getHostname(), details.getCoin(),
                            new WalletNotifyBody(details.getTransactionHash(),
                                    Collections.singletonList(details.getWalletContractAddress()),
                                    details.getBlockNumber()));
                }
            });

            // TODO check erc20 transfer for account

            contractsRepository.findById(tokenID).ifPresent(erc20 -> {
                if (erc20.getContractAddresses().contains(details.getAddress())) {
                    contractsRepository.findById(walletID).ifPresent(wallets -> {

                        List<String> contractAddresses = wallets.getContractAddresses();
                        String fromAddress = (String) details.getIndexedParameters().get(0).getValue();
                        String toAddress = (String) details.getIndexedParameters().get(1).getValue();

                        if (contractAddresses.contains(fromAddress)) {
                            details.setWalletContractAddress(fromAddress);
                        } else if (contractAddresses.contains(toAddress)) {
                            details.setWalletContractAddress(toAddress);
                        }

                        if (details.getWalletContractAddress() == null) {

                            for (String wallet : contractAddresses) {

                                WalletContract walletContract = WalletContract.load(wallet, web3j, new ReadonlyTransactionManager(web3j, wallet), new DefaultGasProvider());

                                try {
                                    List<String> addresses = walletContract.getForwarders().send();
                                    if (addresses.contains(fromAddress)) {
                                        details.setWalletContractAddress(fromAddress);
                                        break;
                                    } else if (addresses.contains(toAddress)) {
                                        details.setWalletContractAddress(toAddress);
                                        break;
                                    }
                                } catch (Exception ignored) {
                                }

                            }

                        }

                        if (details.getWalletContractAddress() != null) {
                            contractEventDetailsRepository.save(details);
                            pusherAPI.sendWalletNotify(details.getHostname(), details.getAddress(),
                                    new WalletNotifyBody(details.getTransactionHash(),
                                            Collections.singletonList(details.getWalletContractAddress()),
                                            details.getBlockNumber()));
                        }

                    });
                }
            });
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
}
