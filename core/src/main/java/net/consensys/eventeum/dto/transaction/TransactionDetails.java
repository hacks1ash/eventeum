package net.consensys.eventeum.dto.transaction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
@Data
@ToString
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDetails {

    @Id
    @org.springframework.data.annotation.Id
    private String id;

    private String hash;

    private String nonce;

    private String blockHash;

    private String blockNumber;

    private String transactionIndex;

    private String from;

    private String to;

    private String value;

    private String nodeName;

    private String contractAddress;

    private String input;

    private String revertReason;

    private String coin;

    private String hostname;

    private String gasPrice;

    private String gas;

    private TransactionStatus status;

}

