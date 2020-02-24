package net.consensys.eventeum.endpoint;

import net.consensys.eventeum.Contracts;
import net.consensys.eventeum.ContractsRepository;
import net.consensys.eventeum.dto.event.filter.ContractEventFilter;
import net.consensys.eventeum.endpoint.response.AddEventFilterResponse;
import net.consensys.eventeum.service.SubscriptionService;
import net.consensys.eventeum.service.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A REST endpoint for adding a removing event filters.
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
@RestController
@RequestMapping(value = "/api/rest/v1/event-filter")
public class ContractEventFilterEndpoint {

    private ContractsRepository contractsRepository;

    private SubscriptionService filterService;

    @Value("${contracts.database.token.ID}")
    private String tokenID;

    @Value("${contracts.database.wallet.ID}")
    private String walletID;

    /**
     * Adds an event filter with the specification described in the ContractEventFilter.
     *
     * @param eventFilter the event filter to add
     * @param response    the http response
     */
    @RequestMapping(method = RequestMethod.POST)
    public AddEventFilterResponse addEventFilter(@RequestBody ContractEventFilter eventFilter,
                                                 HttpServletResponse response) {

        final ContractEventFilter registeredFilter = filterService.registerContractEventFilter(eventFilter, true);
        response.setStatus(HttpServletResponse.SC_ACCEPTED);

        saveContract(eventFilter, walletID);

        return new AddEventFilterResponse(registeredFilter.getId());
    }

    /**
     * Returns the list of registered {@link ContractEventFilter}
     *
     * @param response the http response
     */
    @RequestMapping(method = RequestMethod.GET)
    public List<ContractEventFilter> listEventFilters(HttpServletResponse response) {
        List<ContractEventFilter> registeredFilters = filterService.listContractEventFilters();
        response.setStatus(HttpServletResponse.SC_OK);

        return registeredFilters;
    }

    /**
     * Deletes an event filter with the corresponding filter id.
     *
     * @param filterId the filterId to delete
     * @param response the http response
     */
    @RequestMapping(value = "/{filterId}", method = RequestMethod.DELETE)
    public void removeEventFilter(@PathVariable String filterId,
                                  HttpServletResponse response) {

        try {
            filterService.unregisterContractEventFilter(filterId, true);
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (NotFoundException e) {
            //Rethrow endpoint exception with response information
            throw new FilterNotFoundEndpointException();
        }
    }

    @RequestMapping(value = "/erc20", method = RequestMethod.POST)
    public AddEventFilterResponse addERC20Filter(@RequestBody ContractEventFilter eventFilter,
                                                 HttpServletResponse response) {

        saveContract(eventFilter, tokenID);

        final ContractEventFilter registeredFilter = filterService.registerContractEventFilter(eventFilter, true);
        response.setStatus(HttpServletResponse.SC_ACCEPTED);

        return new AddEventFilterResponse(registeredFilter.getId());

    }

    private void saveContract(@RequestBody ContractEventFilter eventFilter, String walletID) {
        this.contractsRepository.findById(walletID).ifPresentOrElse(contracts -> {
            contracts.getContractAddresses().add(new Contracts.NameContracts(eventFilter.getCoin(), eventFilter.getContractAddress()));
            this.contractsRepository.save(contracts);
        }, () -> this.contractsRepository.save(new Contracts(walletID, Set.copyOf(Collections.singletonList(new Contracts.NameContracts(eventFilter.getCoin(), eventFilter.getContractAddress()))))));
    }

    @Autowired
    public void setContractsRepository(ContractsRepository contractsRepository) {
        this.contractsRepository = contractsRepository;
    }

    @Autowired
    public void setFilterService(SubscriptionService filterService) {
        this.filterService = filterService;
    }
}
