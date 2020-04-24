package net.consensys.eventeum.cws.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.persistence.*;
import java.math.BigInteger;
import java.util.List;


@Document
@Entity
@Data
@ToString
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @javax.persistence.Id
    private String id;

    private String txid;

    private String contractAddress;

    @Enumerated(value = EnumType.STRING)
    private TransactionType transactionType;

    private String coin;

    private BigInteger value;

    private BigInteger baseValue;

    private BigInteger blockChainFee;

    private long createdTime;

    private BigInteger blockNumber;

    @Lob
    @ElementCollection
    private List<TransactionAddress> addresses;

    private long confirmations;

}
