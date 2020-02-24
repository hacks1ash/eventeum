package net.consensys.eventeum.integration.broadcast.blockchain;

import net.consensys.eventeum.dto.block.BlockDetails;
import net.consensys.eventeum.dto.event.ContractEventDetails;
import net.consensys.eventeum.dto.event.filter.ContractEventFilter;
import net.consensys.eventeum.dto.message.BlockEvent;
import net.consensys.eventeum.dto.message.ContractEvent;
import net.consensys.eventeum.dto.message.EventeumMessage;
import net.consensys.eventeum.dto.message.TransactionEvent;
import net.consensys.eventeum.dto.transaction.TransactionDetails;
import net.consensys.eventeum.integration.KafkaSettings;
import net.consensys.eventeum.integration.consumer.SaveConsumer;
import net.consensys.eventeum.utils.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.repository.CrudRepository;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;

/**
 * A BlockchainEventBroadcaster that broadcasts the events to a Kafka queue.
 * <p>
 * The key for each message will defined by the correlationIdStrategy if configured,
 * or a combination of the transactionHash, blockHash and logIndex otherwise.
 * <p>
 * The topic names for block and contract events can be configured via the
 * kafka.topic.contractEvents and kafka.topic.blockEvents properties.
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
public class KafkaBlockchainEventBroadcaster implements BlockchainEventBroadcaster {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBlockchainEventBroadcaster.class);

    private KafkaTemplate<String, EventeumMessage> kafkaTemplate;

    private KafkaSettings kafkaSettings;

    private CrudRepository<ContractEventFilter, String> filterRespository;

    private SaveConsumer saveConsumer;

    public KafkaBlockchainEventBroadcaster(KafkaTemplate<String, EventeumMessage> kafkaTemplate,
                                           KafkaSettings kafkaSettings,
                                           CrudRepository<ContractEventFilter, String> filterRepository,
                                           SaveConsumer saveConsumer) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaSettings = kafkaSettings;
        this.filterRespository = filterRepository;
        this.saveConsumer = saveConsumer;
    }

    @Override
    public void broadcastNewBlock(BlockDetails block) {
        final EventeumMessage<BlockDetails> message = createBlockEventMessage(block);
        LOG.info("Sending block message: " + JSON.stringify(message));

        kafkaTemplate.send(kafkaSettings.getBlockEventsTopic(), message.getId(), message);
    }

    @Override
    public void broadcastContractEvent(ContractEventDetails eventDetails) {
        final EventeumMessage<ContractEventDetails> message = createContractEventMessage(eventDetails);
        LOG.info("Sending contract event message: " + JSON.stringify(message));
        if (saveConsumer.saveContractTransfer(eventDetails)) {
            kafkaTemplate.send(kafkaSettings.getContractEventsTopic(), getContractEventCorrelationId(message), message);
        }
    }

    @Override
    public void broadcastTransaction(TransactionDetails transactionDetails) {
        final EventeumMessage<TransactionDetails> message = createTransactionEventMessage(transactionDetails);
        LOG.info("Sending transaction event message: " + JSON.stringify(message));
        saveConsumer.saveTransaction(transactionDetails);
        kafkaTemplate.send(kafkaSettings.getTransactionEventsTopic(), transactionDetails.getBlockHash(), message);
    }

    protected EventeumMessage<BlockDetails> createBlockEventMessage(BlockDetails blockDetails) {
        return new BlockEvent(blockDetails);
    }

    protected EventeumMessage<ContractEventDetails> createContractEventMessage(ContractEventDetails contractEventDetails) {
        return new ContractEvent(contractEventDetails);
    }

    protected EventeumMessage<TransactionDetails> createTransactionEventMessage(TransactionDetails transactionDetails) {
        return new TransactionEvent(transactionDetails);
    }

    private String getContractEventCorrelationId(EventeumMessage<ContractEventDetails> message) {
        final Optional<ContractEventFilter> filter = filterRespository.findById(message.getDetails().getFilterId());

        if (!filter.isPresent() || filter.get().getCorrelationIdStrategy() == null) {
            return message.getId();
        }

        return filter
                .get()
                .getCorrelationIdStrategy()
                .getCorrelationId(message.getDetails());
    }
}
