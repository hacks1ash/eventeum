package net.consensys.eventeum.endpoint;

import lombok.AllArgsConstructor;
import net.consensys.eventeum.Contracts;
import net.consensys.eventeum.ContractsRepository;
import net.consensys.eventeum.endpoint.response.MonitorTransactionsResponse;
import net.consensys.eventeum.model.TransactionMonitoringSpec;
import net.consensys.eventeum.service.TransactionMonitoringService;
import net.consensys.eventeum.service.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.Set;

/**
 * A REST endpoint for adding a removing event filters.
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
@RestController
@RequestMapping(value = "/api/rest/v1/transaction")
public class TransactionMonitoringEndpoint {

    private TransactionMonitoringService monitoringService;

    private ContractsRepository contractsRepository;

    @Value("${contracts.database.account.ID}")
    private String accountID;

    /**
     * Monitors a transaction with the specified hash, on a specific node
     *
     * @param response the http response
     */
    @RequestMapping(method = RequestMethod.POST)
    public MonitorTransactionsResponse monitorTransactions(@RequestBody TransactionMonitoringSpec spec,
                                                           HttpServletResponse response) {
        spec.generateId();
        spec.convertToCheckSum();
        monitoringService.registerTransactionsToMonitor(spec);
        saveContract(spec, accountID);
        response.setStatus(HttpServletResponse.SC_ACCEPTED);
        return new MonitorTransactionsResponse(spec.getId());
    }

    /**
     * Stops monitoring a transaction with the specfied hash
     *
     * @param @param   specId the id of the transaction monitor to remove
     * @param nodeName the name of the node where the transaction is being monitored
     * @param response the http response
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public void stopMonitoringTransaction(@PathVariable String id,
                                          @RequestParam(required = false) String nodeName,
                                          HttpServletResponse response) {

        try {
            monitoringService.stopMonitoringTransactions(id);
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (NotFoundException e) {
            //Rethrow endpoint exception with response information
            throw new TransactionNotFoundEndpointException();
        }
    }

    private void saveContract(@RequestBody TransactionMonitoringSpec eventFilter, String walletID) {
        this.contractsRepository.findById(walletID).ifPresentOrElse(contracts -> {
            contracts.getContractAddresses().add(new Contracts.NameContracts(eventFilter.getCoin(), eventFilter.getTransactionIdentifierValue(), null));
            this.contractsRepository.save(contracts);
        }, () -> this.contractsRepository.save(new Contracts(walletID, Set.copyOf(Collections.singletonList(new Contracts.NameContracts(eventFilter.getCoin(), eventFilter.getTransactionIdentifierValue().toLowerCase(), null))))));
    }

    @Autowired
    public void setContractsRepository(ContractsRepository contractsRepository) {
        this.contractsRepository = contractsRepository;
    }

    @Autowired
    public void setMonitoringService(TransactionMonitoringService transactionMonitoringService) {
        this.monitoringService = transactionMonitoringService;
    }

}