package net.consensys.eventeum;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractsRepository extends MongoRepository<Contracts, String> {
}
