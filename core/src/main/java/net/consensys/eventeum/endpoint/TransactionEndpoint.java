package net.consensys.eventeum.endpoint;

import lombok.SneakyThrows;
import net.consensys.eventeum.cws.response.Transaction;
import net.consensys.eventeum.cws.response.TransactionType;
import net.consensys.eventeum.cws.response.storage.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;
import java.util.List;

@RestController
@RequestMapping(value = "/api/rest/v1/transaction")
public class TransactionEndpoint {

    private TransactionRepository transactionRepository;

    @Value("${ether.node.url}")
    private String web3URL;

    @SneakyThrows
    @RequestMapping(value = "/{contractAddress}")
    public List<Transaction> getTransactions(@PathVariable(value = "contractAddress") String contractAddress,
                                             @RequestParam(value = "transactionType", required = false) TransactionType transactionType,
                                             @RequestParam(value = "fromTime", required = false) Long fromTime,
                                             @RequestParam(value = "toTime", required = false) Long toTime,
                                             @RequestParam(value = "coin", required = false) String coin) {

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

        List<Transaction> transactions = transactionRepository.findAllByContractAddressAndCoinAndTransactionTypeAndCreatedTimeBetween(contractAddress, coin, transactionTypeString, fromTime, toTime, Sort.by("createdTime").descending());
        Web3j web3j = Web3j.build(new HttpService(web3URL));
        BigInteger latestBlock = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber();
        transactions.forEach(t -> t.setConfirmations(latestBlock.subtract(t.getBlockNumber()).longValueExact()));
        return transactions;

    }

    @Autowired
    public void setTransactionRepository(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

}

