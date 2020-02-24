package net.consensys.eventeum.integration.consumer;

import lombok.SneakyThrows;
import net.consensys.eventeum.Contracts;
import net.consensys.eventeum.ContractsRepository;
import net.consensys.eventeum.WalletContract;
import net.consensys.eventeum.cws.response.Transaction;
import net.consensys.eventeum.cws.response.TransactionAddress;
import net.consensys.eventeum.cws.response.TransactionType;
import net.consensys.eventeum.cws.response.storage.TransactionRepository;
import net.consensys.eventeum.dto.event.ContractEventDetails;
import net.consensys.eventeum.dto.event.parameter.EventParameter;
import net.consensys.eventeum.dto.transaction.TransactionDetails;
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

    private TransactionRepository transactionRepository;

    @SneakyThrows
    public boolean saveContractTransfer(ContractEventDetails details) {
        Web3j web3j = Web3j.build(new HttpService(web3URL));
        BigInteger latestBlock = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber();
        Transaction transaction = null;
        EthBlock.Block block = web3j.ethGetBlockByHash(details.getBlockHash(), false).send().getBlock();

        switch (details.getName()) {
            case "Transacted": {
                TransactionReceipt transactionReceipt = web3j.ethGetTransactionReceipt(details.getTransactionHash()).send().getTransactionReceipt().get();
                org.web3j.protocol.core.methods.response.Transaction responseTx = web3j.ethGetTransactionByHash(details.getTransactionHash()).send().getTransaction().get();
                BigInteger gasUsed = transactionReceipt.getGasUsed();
                BigInteger gasPrice = responseTx.getGasPrice();
                BigInteger blockchainFee = gasPrice.multiply(gasUsed).negate();
                List<BigInteger> values = new ArrayList<>();
                List<String> addresses = new ArrayList<>();
                List<TransactionAddress> transactionAddresses = new ArrayList<>();

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

                transaction = Transaction.builder()
                        .txid(details.getTransactionHash())
                        .contractAddress(details.getAddress().toLowerCase())
                        .transactionType(TransactionType.SEND)
                        .coin(details.getCoin())
                        .baseValue(baseValue)
                        .value(baseValue.add(blockchainFee))
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

                List<TransactionAddress> transactionAddresses = Collections.singletonList(new TransactionAddress(
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

                Optional<Contracts> byId = contractsRepository.findById(walletID);
                if (!byId.isEmpty()) {
                    wallets = byId.get();
                }

                // WALLET CONTRACTS check
                if (wallets != null) {

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
                                    details.setWalletContractAddress(toAddress);
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
                        List<String> accountAddresses = wallets.getContractAddresses().stream().map(Contracts.NameContracts::getContractAddress).collect(Collectors.toList());

                        if (accountAddresses.contains(fromAddress)) {
                            details.setWalletContractAddress(fromAddress);
                            transactionType = TransactionType.SEND;
                            TransactionReceipt transactionReceipt = web3j.ethGetTransactionReceipt(details.getTransactionHash()).send().getTransactionReceipt().get();
                            org.web3j.protocol.core.methods.response.Transaction responseTx = web3j.ethGetTransactionByHash(details.getTransactionHash()).send().getTransaction().get();
                            BigInteger gasUsed = transactionReceipt.getGasUsed();
                            BigInteger gasPrice = responseTx.getGasPrice();
                            blockchainFee = blockchainFee.add(gasPrice.multiply(gasUsed).negate());
                        } else if (accountAddresses.contains(toAddress)) {
                            details.setWalletContractAddress(toAddress);
                            transactionType = TransactionType.RECEIVE;
                        }

                    }
                }


                if (details.getWalletContractAddress() != null) {

                    List<TransactionAddress> transactionAddresses = Collections.singletonList(new TransactionAddress(details.getWalletContractAddress(), baseValue));

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

        return saveToDB(transaction);
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

            String to = details.getTo();
            String from = details.getFrom();

            accounts.getContractAddresses().stream().findFirst().ifPresent(n -> details.setCoin(n.getCoin()));

            Set<String> accountAddresses = accounts.getContractAddresses().stream().map(Contracts.NameContracts::getContractAddress).collect(Collectors.toSet());

            if (accountAddresses.contains(to)) {
                details.setContractAddress(to);
                transactionType = TransactionType.RECEIVE;
            } else if (accountAddresses.contains(from)) {
                details.setContractAddress(from);
                transactionType = TransactionType.SEND;
            }

            transaction = Transaction.builder()
                    .txid(details.getHash())
                    .contractAddress(details.getContractAddress())
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

    private boolean saveToDB(Transaction transaction) {
        if (transaction != null) {
            transactionRepository.findByTxid(transaction.getTxid()).ifPresentOrElse(
                    transactionDetails -> {
                        transactionRepository.delete(transactionDetails);
                        transactionRepository.save(transaction);
                    }, () -> transactionRepository.save(transaction)
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
    public void setTransactionRepository(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }


}
