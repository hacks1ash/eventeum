package net.consensys.eventeum;

import net.consensys.eventeum.dto.transaction.TransactionDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionDetailsRepository extends MongoRepository<TransactionDetails, String> {
}
