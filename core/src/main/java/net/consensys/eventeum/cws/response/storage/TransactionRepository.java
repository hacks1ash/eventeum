package net.consensys.eventeum.cws.response.storage;

import net.consensys.eventeum.cws.response.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {

    Optional<Transaction> findByTxid(String txid);

    @Query("{$and :["
            + "?#{ [0] == null ? { $where : 'true'} : { 'contractAddress' : [0] } },"
            + "?#{ [1] == null ? { $where : 'true'} : { 'coin' : [1] } },"
            + "?#{ [2] == null ? { $where : 'true'} : { 'transactionType' : [2] } },"
            + "?#{ [3] == null ? { $where : 'true'} : { 'createdTime' : [3] } }"
            + "]}")
    List<Transaction> findAllByContractAddressAndCoinAndTransactionTypeAndCreatedTimeBetween(String contractAddress, String coin, String transactionType, Long createdTime, Long createdTime2, Pageable pageable);

}
