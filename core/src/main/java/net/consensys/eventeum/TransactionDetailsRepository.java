package net.consensys.eventeum;

import net.consensys.eventeum.dto.transaction.TransactionDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionDetailsRepository extends MongoRepository<TransactionDetails, String> {

    Optional<TransactionDetails> findByHash(String hash);

}
