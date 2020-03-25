package net.consensys.eventeum.cws.response.storage;

import net.consensys.eventeum.cws.response.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {

    Optional<Transaction> findByTxid(String txid);

    Optional<Transaction> findByTxidAndContractAddress(String txid, String contractAddress);

    @Query("{$and :["
            + "?#{ [0] == null ? { $where : 'true'} : { 'contractAddress' : [0] } },"
            + "?#{ [1] == null ? { $where : 'true'} : { 'coin' : [1] } },"
            + "?#{ [2] == null ? { $where : 'true'} : { 'transactionType' : [2] } },"
            + "?#{ [3] == null ? { $where : 'true'} : { 'createdTime' : { $gt : [3], $lt: [4]} } }"
            + "]}")
    List<Transaction> findAllByContractAddressAndCoinAndTransactionTypeAndCreatedTimeBetween(String contractAddress, String coin, String transactionType, Long createdTime, Long createdTime2, Sort time);

}