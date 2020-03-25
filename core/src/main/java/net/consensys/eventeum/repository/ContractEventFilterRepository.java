package net.consensys.eventeum.repository;

import net.consensys.eventeum.dto.event.filter.ContractEventFilter;
import net.consensys.eventeum.factory.ContractEventFilterRepositoryFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring repository for storing active ContractEventFilters in DB.
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
@Repository
@ConditionalOnMissingBean(ContractEventFilterRepositoryFactory.class)
public interface ContractEventFilterRepository extends CrudRepository<ContractEventFilter, String> {

    Optional<ContractEventFilter> findByContractAddressAndEventSpecification_EventName(String contractAddress, String eventSpecification_eventName);

    Optional<ContractEventFilter> findByCoin(String coin);

}
