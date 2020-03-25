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
import net.consensys.eventeum.dto.transaction.TransactionDetails;
import net.consensys.eventeum.repository.ContractEventFilterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.ReadonlyTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

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

    @Value("${ether.node.url}")
    private String web3URL;

    private ContractsRepository contractsRepository;

    private ContractEventFilterRepository contractEventFilterRepository;

    private TransactionRepository transactionRepository;

    private static final String SEND_TOKEN = "99de6ba0";
    private static final String CREATE_FORWARDER = "a68a76cc";
    private static final String SEND_ETHER = "21802b59";
    private static final String WALLET_DEPLOY = WalletContract.BINARY;

    @SneakyThrows
    public boolean saveContractTransfer(ContractEventDetails details) {
        Web3j web3j = Web3j.build(new HttpService(web3URL));
        BigInteger latestBlock = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber();
        Transaction transaction = null;
        EthBlock.Block block = web3j.ethGetBlockByHash(details.getBlockHash(), false).send().getBlock();
        List<TransactionAddress> transactionAddresses = new ArrayList<>();
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

                BigInteger value = BigInteger.ZERO;
                if (!details.getCoin().contains("eth")) {
                    value = baseValue;
                } else {
                    value = baseValue.add(blockchainFee);
                }

                transaction = Transaction.builder()
                        .txid(details.getTransactionHash())
                        .contractAddress(details.getAddress().toLowerCase())
                        .transactionType(TransactionType.SEND)
                        .coin(details.getCoin())
                        .baseValue(baseValue)
                        .value(value)
                        .createdTime(block.getTimestamp().longValue())
                        .blockChainFee(blockchainFee)
                        .addresses(transactionAddresses)
                        .confirmations(latestBlock.subtract(details.getBlockNumber()).longValue())
                        .build();

                break;
            }
            case "Deposited": {

                BigInteger txBaseValue = (BigInteger) details.getNonIndexedParameters().get(0).getValue();
                String address = details.getIndexedParameters().get(0).getValueString().toLowerCase();
                String contractAddress = details.getAddress().toLowerCase();
                // WALLET CONTRACTS check
                WalletContract walletContract = WalletContract.load(contractAddress, web3j, new ReadonlyTransactionManager(web3j, contractAddress), new DefaultGasProvider());

                List<String> addresses = walletContract.getForwarders().send();
                if (!addresses.contains(contractAddress) || !address.equals(contractAddress)) {
                    address = contractAddress;
                }

                transactionAddresses = Collections.singletonList(new TransactionAddress(
                        address,
                        (BigInteger) details.getNonIndexedParameters().get(0).getValue()
                ));

                transaction = Transaction.builder()
                        .txid(details.getTransactionHash())
                        .contractAddress(contractAddress)
                        .transactionType(TransactionType.RECEIVE)
                        .coin(details.getCoin())
                        .baseValue(txBaseValue)
                        .value(txBaseValue)
                        .createdTime(block.getTimestamp().longValue())
                        .blockChainFee(BigInteger.ZERO)
                        .addresses(transactionAddresses)
                        .confirmations(latestBlock.subtract(details.getBlockNumber()).longValue())
                        .build();

                break;
            }
            case "Transfer": {
                // TODO check account ERC20 Transfers

                Contracts contracts = contractsRepository.findById(tokenID).get();
                contracts.getContractAddresses().stream().findFirst().ifPresent(n -> details.setCoin(n.getCoin()));

                String fromAddress = ((String) details.getIndexedParameters().get(0).getValue()).toLowerCase();
                String toAddress = ((String) details.getIndexedParameters().get(1).getValue()).toLowerCase();
                BigInteger baseValue = (BigInteger) details.getNonIndexedParameters().get(0).getValue();

                Contracts wallets = null;
                TransactionType transactionType = null;
                BigInteger blockchainFee = BigInteger.ZERO;

                if (details.getWalletContractAddress() != null) {
                    Optional<Transaction> trans = transactionRepository.findByTxidAndContractAddress(details.getTransactionHash(), details.getWalletContractAddress());
                    if (trans.isPresent()) {
                        transactionType = trans.get().getTransactionType();
                        transactionAddresses.addAll(trans.get().getAddresses());
                        blockchainFee = trans.get().getBlockChainFee();
                    }
                }

                Optional<Contracts> byId = contractsRepository.findById(walletID);
                if (!byId.isEmpty()) {
                    wallets = byId.get();
                }

                // WALLET CONTRACTS check
                if (wallets != null && details.getWalletContractAddress() == null) {

                    List<String> contractAddresses = wallets.getContractAddresses().stream().map(Contracts.NameContracts::getContractAddress).collect(Collectors.toList());

                    if (contractAddresses.contains(fromAddress)) {
                        return false;
                    } else if (contractAddresses.contains(toAddress)) {
                        details.setWalletContractAddress(toAddress);
                        transactionType = TransactionType.RECEIVE;
                    }

                    if (details.getWalletContractAddress() == null) {

                        for (String wallet : contractAddresses) {

                            WalletContract walletContract = WalletContract.load(wallet, web3j, new ReadonlyTransactionManager(web3j, wallet), new DefaultGasProvider());

                            try {
                                List<String> addresses = walletContract.getForwarders().send();
                                if (addresses.contains(fromAddress)) {
                                    return false;
                                } else if (addresses.contains(toAddress)) {
                                    details.setWalletContractAddress(wallet);
                                    transactionAddresses.add(new TransactionAddress(toAddress, baseValue));
                                    transactionType = TransactionType.RECEIVE;
                                    break;
                                }
                            } catch (Exception ignored) {
                            }

                        }

                    }


                }

                // ACCOUNT CHECKS
                if (details.getWalletContractAddress() == null) {
                    Contracts accounts = null;
                    Optional<Contracts> accountsById = contractsRepository.findById(accountID);
                    if (!accountsById.isEmpty()) {
                        accounts = accountsById.get();
                    }

                    if (accounts != null) {
                        List<String> accountAddresses = accounts.getContractAddresses().stream().map(t -> t.getContractAddress().toLowerCase()).collect(Collectors.toList());

                        if (accountAddresses.contains(fromAddress)) {
                            details.setWalletContractAddress(fromAddress);
                            transactionType = TransactionType.SEND;
                            TransactionReceipt transactionReceipt = web3j.ethGetTransactionReceipt(details.getTransactionHash()).send().getTransactionReceipt().get();
                            org.web3j.protocol.core.methods.response.Transaction responseTx = web3j.ethGetTransactionByHash(details.getTransactionHash()).send().getTransaction().get();
                            BigInteger gasUsed = transactionReceipt.getGasUsed();
                            BigInteger gasPrice = responseTx.getGasPrice();
                            baseValue = baseValue.negate();
                            blockchainFee = gasPrice.multiply(gasUsed).negate();
                        } else if (accountAddresses.contains(toAddress)) {
                            details.setWalletContractAddress(toAddress);
                            transactionType = TransactionType.RECEIVE;
                        }

                    }
                }


                if (details.getWalletContractAddress() != null) {

                    if (transactionAddresses.isEmpty()) {
                        transactionAddresses.add(new TransactionAddress(details.getWalletContractAddress(), baseValue));
                    }

                    if (transactionType == TransactionType.SEND) {
                        if (baseValue.compareTo(BigInteger.ZERO) > 0) {
                            baseValue = baseValue.negate();
                        }
                    }

                    transaction = Transaction.builder()
                            .txid(details.getTransactionHash())
                            .contractAddress(details.getWalletContractAddress())
                            .transactionType(transactionType)
                            .coin(details.getCoin())
                            .baseValue(baseValue)
                            .value(baseValue.add(blockchainFee))
                            .createdTime(block.getTimestamp().longValue())
                            .blockChainFee(blockchainFee)
                            .addresses(transactionAddresses)
                            .confirmations(latestBlock.subtract(details.getBlockNumber()).longValue())
                            .build();

                }
                break;
            }
        }
        if (saveToDB(transaction)) {
            return updateBlockForContracts(details.getAddress(), details.getName(), details.getCoin(), details.getBlockNumber());
        }
        return false;
    }

    @SneakyThrows
    public boolean saveTransaction(TransactionDetails details) {

        Web3j web3j = Web3j.build(new HttpService(web3URL));
        BigInteger latestBlock = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber();
        Transaction transaction = null;
        EthBlock.Block block = web3j.ethGetBlockByHash(details.getBlockHash(), false).send().getBlock();
        TransactionType transactionType = null;
        BigInteger baseValue = BigInteger.ZERO;
        BigInteger blockchainFee = BigInteger.ZERO;
        List<TransactionAddress> transactionAddresses = new ArrayList<>();

        Contracts accounts = null;
        Optional<Contracts> byId = contractsRepository.findById(accountID);
        if (!byId.isEmpty()) {
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
                details.setContractAddress(to);
                transactionAddresses.add(new TransactionAddress(to, baseValue));
                transactionType = TransactionType.RECEIVE;
            } else if (accountAddresses.contains(from)) {
                details.setContractAddress(from);
                baseValue = baseValue.negate();
                blockchainFee = new BigInteger(details.getGas().substring(2), 16).multiply(new BigInteger(details.getGasPrice().substring(2), 16)).negate();

                if (details.getInput().length() > 2) {
                    String dataWithoutPrefix = details.getInput().substring(2);
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
                }
            }

            transaction = Transaction.builder()
                    .txid(details.getHash())
                    .contractAddress(details.getContractAddress().toLowerCase())
                    .createdTime(block.getTimestamp().longValue())
                    .transactionType(transactionType)
                    .baseValue(baseValue)
                    .blockChainFee(blockchainFee)
                    .value(baseValue.add(blockchainFee))
                    .confirmations(latestBlock.subtract(block.getNumber()).longValue())
                    .coin(details.getCoin())
                    .addresses(transactionAddresses).build();

        }

        return saveToDB(transaction);
    }


    private boolean updateBlockForContracts(String contractAddress, String eventName, String coin, BigInteger blockNumber) {
        log.warn(coin);
        if (!coin.contains("eth")) {
            Optional<ContractEventFilter> byCoin = contractEventFilterRepository.findByCoin(coin);
            if (byCoin.isPresent()) {
                ContractEventFilter contractEventFilter = byCoin.get();
                contractEventFilter.setStartBlock(blockNumber.add(BigInteger.ONE));
                contractEventFilterRepository.save(contractEventFilter);
                if (eventName.equals("Transfer")) {
                    return true;
                }
            }
        }

        Optional<ContractEventFilter> eventFilter = contractEventFilterRepository.findByContractAddressAndEventSpecification_EventName(contractAddress.toLowerCase(), eventName);
        if (eventFilter.isPresent()) {
            ContractEventFilter contractEventFilter = eventFilter.get();
            contractEventFilter.setStartBlock(blockNumber.add(BigInteger.ONE));
            contractEventFilterRepository.save(contractEventFilter);
            return true;
        }
        return false;
    }

    private boolean saveToDB(Transaction transaction) {
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
            return true;
        }
        return false;
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


}
