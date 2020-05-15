package net.consensys.eventeum.integration.consumer;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.consensys.eventeum.Contracts;
import net.consensys.eventeum.ContractsRepository;
import net.consensys.eventeum.WalletContract;
import net.consensys.eventeum.cws.response.Transaction;
import net.consensys.eventeum.cws.response.TransactionAddress;
import net.consensys.eventeum.cws.response.TransactionType;
import net.consensys.eventeum.cws.response.storage.TransactionRepository;
import net.consensys.eventeum.dto.event.ContractEventDetails;
import net.consensys.eventeum.dto.event.filter.ContractEventFilter;
import net.consensys.eventeum.dto.event.parameter.EventParameter;
import net.consensys.eventeum.dto.message.WebhookMessage;
import net.consensys.eventeum.dto.transaction.TransactionDetails;
import net.consensys.eventeum.integration.consumer.model.Webhook;
import net.consensys.eventeum.repository.ContractEventFilterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SaveConsumer {

    @Value("${contracts.database.token.ID}")
    private String tokenID;

    @Value("${contracts.database.wallet.ID}")
    private String walletID;

    @Value("${contracts.database.account.ID}")
    private String accountID;

    @Value("${cws.hostname:cwsprod}")
    private String hostname;

    private Web3j web3j;

    private ContractsRepository contractsRepository;

    private ContractEventFilterRepository contractEventFilterRepository;

    private TransactionRepository transactionRepository;

    private static final String SEND_TOKEN = "99de6ba0";
    private static final String CREATE_FORWARDER = "a68a76cc";
    private static final String SEND_ETHER = "21802b59";
    private static final String WALLET_DEPLOY = WalletContract.BINARY;
    private static final String SEND_TOKEN_SINGLE = "a9059cbb";

    @SneakyThrows
    public List<WebhookMessage> saveContractTransfer(ContractEventDetails details) {

        List<TransactionAddress> transactionAddresses = new ArrayList<>();
        Contracts wallets = null;
        Contracts accounts = null;

        List<Transaction> result = new ArrayList<>();

        switch (details.getName()) {
            case "Transacted": {
                TransactionReceipt transactionReceipt = web3j.ethGetTransactionReceipt(details.getTransactionHash()).send().getTransactionReceipt().get();
                org.web3j.protocol.core.methods.response.Transaction responseTx = web3j.ethGetTransactionByHash(details.getTransactionHash()).send().getTransaction().get();
                BigInteger gasUsed = transactionReceipt.getGasUsed();
                BigInteger gasPrice = responseTx.getGasPrice();
                BigInteger blockchainFee = gasPrice.multiply(gasUsed).negate();
                List<BigInteger> values = new ArrayList<>();
                List<String> addresses = new ArrayList<>();

                String msgSender = details.getIndexedParameters().get(0).getValueString().toLowerCase();
                Optional<Contracts> tokensByID = contractsRepository.findById(tokenID);

                tokensByID.flatMap(contracts -> contracts.getContractAddresses().stream().filter(t -> t.getContractAddress().equals(msgSender))
                        .findFirst()).ifPresent(c -> details.setCoin(c.getCoin()));


                for (Object value : ((ArrayList) details.getNonIndexedParameters().get(0).getValue())) {
                    addresses.add(((EventParameter) value).getValueString().toLowerCase());
                }

                for (Object value : ((ArrayList) details.getNonIndexedParameters().get(1).getValue())) {
                    values.add((BigInteger) ((EventParameter) value).getValue());
                }

                if (values.size() == addresses.size()) {
                    for (int i = 0; i < values.size(); i++) {
                        transactionAddresses.add(new TransactionAddress(addresses.get(i), values.get(i).negate()));
                    }
                }

                BigInteger baseValue = values.stream().reduce(BigInteger.ZERO, BigInteger::add).negate();

                boolean isEth = details.getCoin().contains("eth");

                Optional<Contracts> accountsById = contractsRepository.findById(accountID);
                if (accountsById.isPresent()) {
                    accounts = accountsById.get();
                }
                Optional<Contracts> byId = contractsRepository.findById(walletID);
                if (byId.isPresent()) {
                    wallets = byId.get();
                }

                if (accounts != null) {
                    List<String> accountAddresses = accounts.getContractAddresses().stream().map(t -> t.getContractAddress().toLowerCase()).collect(Collectors.toList());
                    Set<Contracts.NameContracts> walletContracts = wallets.getContractAddresses();
                    for (TransactionAddress transactionAddress : transactionAddresses) {
                        if (accountAddresses.contains(transactionAddress.getAddress())) {
                            result.add(constructTransaction(
                                    details.getTransactionHash(),
                                    transactionAddress.getAddress(),
                                    TransactionType.RECEIVE,
                                    details.getCoin(),
                                    transactionAddress.getAmount().negate(),
                                    transactionAddress.getAddress(),
                                    details.getBlockNumber(),
                                    BigInteger.ZERO,
                                    false
                            ));
                        } else if (!isEth) {
                            for (Contracts.NameContracts contract : walletContracts) {
                                if (contract.getContractAddress().equalsIgnoreCase(transactionAddress.getAddress())) {
                                    result.add(constructTransaction(
                                            details.getTransactionHash(),
                                            transactionAddress.getAddress(),
                                            TransactionType.RECEIVE,
                                            details.getCoin(),
                                            transactionAddress.getAmount().negate(),
                                            transactionAddress.getAddress(),
                                            details.getBlockNumber(),
                                            BigInteger.ZERO,
                                            false
                                    ));
                                } else if (contract.getForwarders() != null && contract.getForwarders().contains(transactionAddress.getAddress())) {
                                    result.add(constructTransaction(
                                            details.getTransactionHash(),
                                            transactionAddress.getAddress(),
                                            TransactionType.RECEIVE,
                                            details.getCoin(),
                                            transactionAddress.getAmount().negate(),
                                            transactionAddress.getAddress(),
                                            details.getBlockNumber(),
                                            BigInteger.ZERO,
                                            false
                                    ));
                                }
                            }
                        }
                    }
                }

                result.add(constructTransaction(
                        details.getTransactionHash(),
                        details.getAddress(),
                        TransactionType.SEND,
                        details.getCoin(),
                        baseValue,
                        transactionAddresses,
                        details.getBlockNumber(),
                        blockchainFee,
                        isEth
                ));

                break;
            }
            case "Deposited": {

                BigInteger txBaseValue = (BigInteger) details.getNonIndexedParameters().get(0).getValue();
                String address = details.getIndexedParameters().get(0).getValueString().toLowerCase();
                String contractAddress = details.getAddress().toLowerCase();

                // WALLET CONTRACTS check
                Optional<Contracts> byId = contractsRepository.findById(walletID);
                if (byId.isPresent()) {
                    wallets = byId.get();
                }

                Optional<Contracts.NameContracts> first = wallets.getContractAddresses().stream().filter(c -> c.getContractAddress().equals(contractAddress)).findFirst();

                if (first.isPresent()) {
                    List<String> addresses = first.get().getForwarders();
                    if (!addresses.contains(contractAddress) || !address.equals(contractAddress)) {
                        address = contractAddress;
                    }

                    result.add(constructTransaction(
                            details.getTransactionHash(),
                            contractAddress,
                            TransactionType.RECEIVE,
                            details.getCoin(),
                            txBaseValue,
                            address,
                            details.getBlockNumber(),
                            BigInteger.ZERO,
                            false
                    ));
                }

                break;
            }
            case "Transfer": {
                Contracts contracts = contractsRepository.findById(tokenID).get();
                Set<Contracts.NameContracts> tokenContractAddresses = contracts.getContractAddresses();
                Contracts.NameContracts nameContracts = tokenContractAddresses.stream().filter(c -> c.getContractAddress().equalsIgnoreCase(details.getAddress())).findFirst().get();
                details.setCoin(nameContracts.getCoin());

                String fromAddress = ((String) details.getIndexedParameters().get(0).getValue()).toLowerCase();
                String toAddress = ((String) details.getIndexedParameters().get(1).getValue()).toLowerCase();
                BigInteger baseValue = (BigInteger) details.getNonIndexedParameters().get(0).getValue();


                TransactionType transactionType = null;
                BigInteger blockchainFee = BigInteger.ZERO;

                if (details.getWalletContractAddress() != null) {
                    Optional<Transaction> trans = transactionRepository.findByTxidAndContractAddress(details.getTransactionHash(), details.getWalletContractAddress());
                    if (trans.isPresent()) {
                        Transaction transaction1 = trans.get();
                        transactionType = transaction1.getTransactionType();
                        transactionAddresses.addAll(transaction1.getAddresses());
                        blockchainFee = transaction1.getBlockChainFee();
                    }
                }

                Optional<Contracts> accountsById = contractsRepository.findById(accountID);
                Optional<Contracts> byId = contractsRepository.findById(walletID);
                if (byId.isPresent()) {
                    wallets = byId.get();
                }

                if (accountsById.isPresent()) {
                    accounts = accountsById.get();
                }

                // WALLET CONTRACTS check
                if (wallets != null) {

                    Set<Contracts.NameContracts> contractsSub = wallets.getContractAddresses();
                    List<String> contractAddresses = contractsSub.stream().map(Contracts.NameContracts::getContractAddress).collect(Collectors.toList());
                    boolean checkForwarder = true;
                    if (contractAddresses.contains(fromAddress)) {
                        return null;
                    } else if (contractAddresses.contains(toAddress)) {
                        result.add(constructTransaction(
                                details.getTransactionHash(),
                                toAddress,
                                TransactionType.RECEIVE,
                                details.getCoin(),
                                baseValue,
                                toAddress,
                                details.getBlockNumber(),
                                BigInteger.ZERO,
                                false
                        ));
                        checkForwarder = false;
                    }

                    if (checkForwarder) {

                        for (Contracts.NameContracts wallet : contractsSub) {
                            try {
                                if (wallet.getForwarders() != null) {
                                    List<String> addresses = wallet.getForwarders();
                                    if (addresses.contains(fromAddress)) {
                                        return null;
                                    } else if (addresses.contains(toAddress)) {
                                        result.add(constructTransaction(
                                                details.getTransactionHash(),
                                                wallet.getContractAddress(),
                                                TransactionType.RECEIVE,
                                                details.getCoin(),
                                                baseValue,
                                                toAddress,
                                                details.getBlockNumber(),
                                                BigInteger.ZERO,
                                                false
                                        ));
                                        break;
                                    }
                                }

                            } catch (Exception ignored) {
                            }

                        }

                    }

                }

                // ACCOUNT CHECKS
                if (accounts != null) {
                    List<String> accountAddresses = accounts.getContractAddresses().stream().map(t -> t.getContractAddress().toLowerCase()).collect(Collectors.toList());

                    if (accountAddresses.contains(fromAddress)) {

                        // Account to Account
                        if (accountAddresses.contains(toAddress)) {
                            result.add(constructTransaction(
                                    details.getTransactionHash(),
                                    toAddress,
                                    TransactionType.RECEIVE,
                                    details.getCoin(),
                                    baseValue,
                                    toAddress,
                                    details.getBlockNumber(),
                                    BigInteger.ZERO,
                                    false
                            ));
                        }

                        Set<Contracts.NameContracts> contractSub = null;
                        List<String> contractAddresses = null;
                        if (wallets != null) {
                            contractSub = wallets.getContractAddresses();
                            contractAddresses = wallets.getContractAddresses().stream().map(Contracts.NameContracts::getContractAddress).collect(Collectors.toList());
                        }

                        // Account to Contract
                        if (contractAddresses != null && transactionType == null) {
                            if (contractAddresses.contains(toAddress)) {
                                details.setWalletContractAddress(toAddress);

                                result.add(constructTransaction(
                                        details.getTransactionHash(),
                                        toAddress,
                                        TransactionType.RECEIVE,
                                        details.getCoin(),
                                        baseValue,
                                        toAddress,
                                        details.getBlockNumber(),
                                        BigInteger.ZERO,
                                        false
                                ));
                            }

                        }

                        // Account to Forwarder
                        if (contractAddresses != null && contractSub != null && transactionType == null) {
                            for (Contracts.NameContracts wallet : contractSub) {
                                try {
                                    List<String> addresses = wallet.getForwarders();
                                    if (addresses.contains(toAddress)) {
                                        result.add(constructTransaction(
                                                details.getTransactionHash(),
                                                toAddress,
                                                TransactionType.RECEIVE,
                                                details.getCoin(),
                                                baseValue,
                                                toAddress,
                                                details.getBlockNumber(),
                                                BigInteger.ZERO,
                                                false
                                        ));
                                        break;
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }

                        TransactionReceipt transactionReceipt = web3j.ethGetTransactionReceipt(details.getTransactionHash()).send().getTransactionReceipt().get();
                        org.web3j.protocol.core.methods.response.Transaction responseTx = web3j.ethGetTransactionByHash(details.getTransactionHash()).send().getTransaction().get();
                        BigInteger gasUsed = transactionReceipt.getGasUsed();
                        BigInteger gasPrice = responseTx.getGasPrice();
                        blockchainFee = gasPrice.multiply(gasUsed).negate();

                        result.add(constructTransaction(
                                details.getTransactionHash(),
                                fromAddress,
                                TransactionType.SEND,
                                details.getCoin(),
                                baseValue.negate(),
                                toAddress,
                                details.getBlockNumber(),
                                blockchainFee,
                                false
                        ));

                    } else if (accountAddresses.contains(toAddress)) {
                        details.setWalletContractAddress(toAddress);

                        result.add(constructTransaction(
                                details.getTransactionHash(),
                                toAddress,
                                TransactionType.RECEIVE,
                                details.getCoin(),
                                baseValue,
                                toAddress,
                                details.getBlockNumber(),
                                blockchainFee,
                                false
                        ));
                    }
                }

                break;
            }
        }

        if (saveToDB(result, details)) {
            return transaformTransactionsToWebhook(result, details);
        }
        return null;
    }

    @SneakyThrows
    public List<WebhookMessage> saveTransaction(TransactionDetails details) {
        EthBlock.Block block = web3j.ethGetBlockByHash(details.getBlockHash(), false).send().getBlock();
        TransactionType transactionType = null;
        BigInteger baseValue = BigInteger.ZERO;
        BigInteger blockchainFee = BigInteger.ZERO;
        List<TransactionAddress> transactionAddresses = new ArrayList<>();

        List<Transaction> result = new ArrayList<>();

        Contracts accounts = null;
        Optional<Contracts> byId = contractsRepository.findById(accountID);
        if (byId.isPresent()) {
            accounts = byId.get();
        }

        if (accounts != null) {
            String to = null;
            if (details.getTo() != null) {
                to = details.getTo().toLowerCase();
            }
            String from = details.getFrom().toLowerCase();

            accounts.getContractAddresses().stream().findFirst().ifPresent(n -> details.setCoin(n.getCoin()));

            Set<String> accountAddresses = accounts.getContractAddresses().stream().map(t -> t.getContractAddress().toLowerCase()).collect(Collectors.toSet());

            baseValue = baseValue.add(new BigInteger(details.getValue().substring(2), 16));

            if (accountAddresses.contains(to)) {
                result.add(constructTransaction(
                        details.getHash(),
                        to,
                        TransactionType.RECEIVE,
                        details.getCoin(),
                        baseValue,
                        to,
                        block.getNumber(),
                        blockchainFee,
                        false
                ));
            }

            if (accountAddresses.contains(from)) {
                details.setContractAddress(from);
                baseValue = baseValue.negate();
                blockchainFee = new BigInteger(details.getGas().substring(2), 16).multiply(new BigInteger(details.getGasPrice().substring(2), 16)).negate();

                if (details.getInput().length() > 2) {
                    String dataWithoutPrefix = details.getInput().substring(2);

                    if (dataWithoutPrefix.startsWith(SEND_TOKEN_SINGLE)) {
                        return null;
                    }

                    if (dataWithoutPrefix.startsWith(SEND_ETHER) || dataWithoutPrefix.startsWith(SEND_TOKEN)) {
                        transactionType = TransactionType.SEND;
                        transactionAddresses.add(new TransactionAddress(to, baseValue));
                    } else if (dataWithoutPrefix.startsWith(CREATE_FORWARDER)) {
                        transactionType = TransactionType.CREATE_ADDRESS;
                        transactionAddresses.add(new TransactionAddress(to, baseValue));
                    } else if (dataWithoutPrefix.equals(WALLET_DEPLOY)) {
                        transactionType = TransactionType.WALLET_CREATION;
                        String contractAddress = web3j.ethGetTransactionReceipt(details.getHash()).send().getTransactionReceipt().get().getContractAddress();
                        transactionAddresses.add(new TransactionAddress(contractAddress, baseValue));
                    } else {
                        transactionAddresses.add(new TransactionAddress(to, baseValue));
                    }
                } else {
                    transactionType = TransactionType.SEND;
                    transactionAddresses.add(new TransactionAddress(to, baseValue));
                }


                result.add(constructTransaction(
                        details.getHash(),
                        details.getContractAddress(),
                        transactionType,
                        details.getCoin(),
                        baseValue,
                        transactionAddresses,
                        block.getNumber(),
                        blockchainFee,
                        true
                ));
            }

        }


        if (saveToDB(result, null)) {
            return transaformTransactionsToWebhook(result, details);
        }
        return null;
    }

    @SneakyThrows
    private long getBlockTimeStamp(BigInteger blockNumber) {
        return web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false).send().getBlock().getTimestamp().longValue();
    }

    private void updateBlockForContracts(ContractEventDetails details) {
        log.warn(details.getCoin());
        if (!details.getCoin().contains("eth")) {
            Optional<ContractEventFilter> byCoin = contractEventFilterRepository.findByCoin(details.getCoin());
            if (byCoin.isPresent()) {
                ContractEventFilter contractEventFilter = byCoin.get();
                contractEventFilter.setStartBlock(details.getBlockNumber().add(BigInteger.ONE));
                contractEventFilterRepository.save(contractEventFilter);
                if (details.getName().equals("Transfer")) {
                    return;
                }
            }
        }

        Optional<ContractEventFilter> eventFilter = contractEventFilterRepository.findByContractAddressAndEventSpecification_EventName(details.getAddress().toLowerCase(), details.getName());
        if (eventFilter.isPresent()) {
            ContractEventFilter contractEventFilter = eventFilter.get();
            contractEventFilter.setStartBlock(details.getBlockNumber().add(BigInteger.ONE));
            contractEventFilterRepository.save(contractEventFilter);
        }
    }

    private void saveToDB(Transaction transaction, ContractEventDetails details) {
        if (transaction != null) {
            transactionRepository.findByTxidAndContractAddress(transaction.getTxid(), transaction.getContractAddress()).ifPresentOrElse(
                    transactionDetails -> {
                        transactionRepository.delete(transactionDetails);
                        if (String.valueOf(transaction.getCreatedTime()).length() == 10) {
                            transaction.setCreatedTime(transaction.getCreatedTime() * 1000);
                        }
                        transactionRepository.save(transaction);
                    }, () -> {
                        if (String.valueOf(transaction.getCreatedTime()).length() == 10) {
                            transaction.setCreatedTime(transaction.getCreatedTime() * 1000);
                        }
                        transactionRepository.save(transaction);
                    }
            );

            if (details != null) {
                updateBlockForContracts(details);
            }
        }
    }

    private boolean saveToDB(List<Transaction> transactions, ContractEventDetails details) {
        if (transactions != null) {
            for (Transaction transaction : transactions) {
                saveToDB(transaction, details);
            }
            return true;
        }
        return false;
    }

    private List<WebhookMessage> transaformTransactionsToWebhook(List<Transaction> transactions, ContractEventDetails details) {
        List<WebhookMessage> result = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (transaction.getTransactionType() == TransactionType.SEND || transaction.getTransactionType() == TransactionType.RECEIVE) {
                result.add(new WebhookMessage(new Webhook(hostname, details.getCoin(), transaction.getTxid(), transaction.getAddresses().stream().map(TransactionAddress::getAddress).collect(Collectors.toList()), transaction.getBlockNumber())));
            }
        }
        return result;
    }

    private List<WebhookMessage> transaformTransactionsToWebhook(List<Transaction> transactions, TransactionDetails details) {
        List<WebhookMessage> result = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (transaction.getTransactionType() == TransactionType.SEND || transaction.getTransactionType() == TransactionType.RECEIVE) {
                result.add(new WebhookMessage(new Webhook(hostname, details.getCoin(), transaction.getTxid(), transaction.getAddresses().stream().map(TransactionAddress::getAddress).collect(Collectors.toList()), transaction.getBlockNumber())));
            }
        }
        return result;
    }

    private Transaction constructTransaction(String txid,
                                             String contractAddress,
                                             TransactionType transactionType,
                                             String coin,
                                             BigInteger amount,
                                             String address,
                                             BigInteger blockNumber,
                                             BigInteger blockchainFee,
                                             boolean sumBlockchainFee) {
        return Transaction.builder()
                .txid(txid)
                .contractAddress(contractAddress.toLowerCase())
                .transactionType(transactionType)
                .coin(coin)
                .value(sumBlockchainFee ? amount.add(blockchainFee) : amount)
                .baseValue(amount)
                .createdTime(getBlockTimeStamp(blockNumber))
                .blockNumber(blockNumber)
                .blockChainFee(blockchainFee)
                .addresses(Arrays.asList(new TransactionAddress(address.toLowerCase(), amount)))
                .build();
    }

    private Transaction constructTransaction(String txid,
                                             String contractAddress,
                                             TransactionType transactionType,
                                             String coin,
                                             BigInteger amount,
                                             List<TransactionAddress> transactionAddresses,
                                             BigInteger blockNumber,
                                             BigInteger blockchainFee,
                                             boolean sumBlockchainFee) {
        return Transaction.builder()
                .txid(txid)
                .contractAddress(contractAddress.toLowerCase())
                .transactionType(transactionType)
                .coin(coin)
                .value(sumBlockchainFee ? amount.add(blockchainFee) : amount)
                .baseValue(amount)
                .createdTime(getBlockTimeStamp(blockNumber))
                .blockNumber(blockNumber)
                .blockChainFee(blockchainFee)
                .addresses(transactionAddresses)
                .build();
    }

    @Autowired
    public void setContractsRepository(ContractsRepository contractsRepository) {
        this.contractsRepository = contractsRepository;
    }

    @Autowired
    public void setContractEventFilterRepository(ContractEventFilterRepository contractEventFilterRepository) {
        this.contractEventFilterRepository = contractEventFilterRepository;
    }

    @Autowired
    public void setTransactionRepository(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Autowired
    public void setWeb3j(Web3j web3j) {
        this.web3j = web3j;
    }


}
