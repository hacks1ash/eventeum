package net.consensys.eventeum.config;

import net.consensys.eventeum.ContractsRepository;
import net.consensys.eventeum.TransactionDetailsRepository;
import net.consensys.eventeum.dto.message.EventeumMessage;
import net.consensys.eventeum.integration.KafkaSettings;
import net.consensys.eventeum.integration.broadcast.internal.DoNothingEventeumEventBroadcaster;
import net.consensys.eventeum.integration.broadcast.internal.EventeumEventBroadcaster;
import net.consensys.eventeum.integration.broadcast.internal.KafkaEventeumEventBroadcaster;
import net.consensys.eventeum.integration.consumer.EventeumInternalEventConsumer;
import net.consensys.eventeum.integration.consumer.KafkaFilterEventConsumer;
import net.consensys.eventeum.integration.eventstore.db.repository.ContractEventDetailsRepository;
import net.consensys.eventeum.repository.TransactionMonitoringSpecRepository;
import net.consensys.eventeum.service.SubscriptionService;
import net.consensys.eventeum.service.TransactionMonitoringService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.support.RetryTemplate;

/**
 * Spring bean configuration for the FilterEvent broadcaster and consumer.
 * <p>
 * If broadcaster.multiInstance is set to true, then register a Kafka broadcaster,
 * otherwise register a dummy broadcaster that does nothing.
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
@Configuration
public class EventeumEventConfiguration {

    @Bean
    @ConditionalOnProperty(name = "broadcaster.multiInstance", havingValue = "true")
    public EventeumEventBroadcaster kafkaFilterEventBroadcaster(KafkaTemplate<String, EventeumMessage> kafkaTemplate,
                                                                KafkaSettings kafkaSettings) {
        return new KafkaEventeumEventBroadcaster(kafkaTemplate, kafkaSettings);
    }

    @Bean
    @ConditionalOnProperty(name = "broadcaster.multiInstance", havingValue = "true")
    public EventeumInternalEventConsumer kafkaFilterEventConsumer(SubscriptionService subscriptionService,
                                                                  TransactionMonitoringService transactionMonitoringService,
                                                                  KafkaSettings kafkaSettings,
                                                                  ContractEventDetailsRepository contractEventDetailsRepository,
                                                                  ContractsRepository contractsRepository,
                                                                  TransactionDetailsRepository transactionDetailsRepository,
                                                                  TransactionMonitoringSpecRepository transactionMonitoringSpecRepository,
                                                                  @Qualifier("cwsRetryTemplate") RetryTemplate retryTemplate) {
        return new KafkaFilterEventConsumer(subscriptionService, transactionMonitoringService, kafkaSettings, contractEventDetailsRepository, contractsRepository, transactionDetailsRepository, transactionMonitoringSpecRepository, retryTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "broadcaster.multiInstance", havingValue = "false")
    public EventeumEventBroadcaster doNothingFilterEventBroadcaster() {
        return new DoNothingEventeumEventBroadcaster();
    }
}
