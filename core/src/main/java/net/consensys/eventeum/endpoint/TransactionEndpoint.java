package net.consensys.eventeum.endpoint;

import net.consensys.eventeum.cws.response.Transaction;
import net.consensys.eventeum.cws.response.TransactionType;
import net.consensys.eventeum.cws.response.storage.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/rest/v1/transaction")
public class TransactionEndpoint {

    private TransactionRepository transactionRepository;

    @RequestMapping(value = "/{contractAddress}")
    public List<Transaction> getTransactions(@PathVariable(value = "contractAddress") String contractAddress,
                                             @RequestParam(value = "transactionType", required = false) TransactionType transactionType,
                                             @RequestParam(value = "fromTime", required = false) Long fromTime,
                                             @RequestParam(value = "toTime", required = false) Long toTime,
                                             @RequestParam(value = "coin", required = false) String coin,
                                             @RequestParam(value = "limit", required = false) Integer limit,
                                             @RequestParam(value = "offset", required = false) Integer offset) {
        PageRequest pagination = null;

        if (limit != null && offset != null) {
            pagination = PageRequest.of(((limit * (offset - 1)) / 10) - 1, limit, Sort.by("createdTime").descending());
        } else if (limit != null) {
            pagination = PageRequest.of(0, limit, Sort.by("createdTime").descending());
        } else {
            pagination = PageRequest.of(0, Integer.MAX_VALUE, Sort.by("createdTime").descending());
        }

        String transactionTypeString = null;

        if (transactionType != null) {
            transactionTypeString = transactionType.toString();
        }

        if (fromTime != null && toTime != null) {
            fromTime -= 1;
            toTime += 1;
        }

        if (fromTime != null && toTime == null) {
            fromTime -= 1;
            toTime = Long.MAX_VALUE;
        }

        if (toTime != null && fromTime == null) {
            toTime += 1;
            fromTime = 0l;
        }

        return transactionRepository.findAllByContractAddressAndCoinAndTransactionTypeAndCreatedTimeBetween(contractAddress, coin, transactionTypeString, fromTime, toTime, pagination);

    }

    @Autowired
    public void setTransactionRepository(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }
}
