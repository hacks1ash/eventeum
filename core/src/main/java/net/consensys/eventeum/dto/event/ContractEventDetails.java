package net.consensys.eventeum.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.consensys.eventeum.dto.event.parameter.EventParameter;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import java.math.BigInteger;
import java.util.List;

/**
 * Represents the details of an emitted Ethereum smart contract event.
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
@Document
@Entity
@Data
@ToString
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContractEventDetails {

    private String name;

    private String filterId;

    private String nodeName;

    private String hostname;

    @Lob
    // required because of https://stackoverflow.com/questions/43412517/sql-string-or-binary-data-would-be-truncated-error/43426863
    @ElementCollection
    private List<EventParameter> indexedParameters;

    @Lob
    @ElementCollection
    private List<EventParameter> nonIndexedParameters;

    @Id
    private String transactionHash;

    private BigInteger logIndex;

    private BigInteger blockNumber;

    private String blockHash;

    private String address;

    private ContractEventStatus status = ContractEventStatus.UNCONFIRMED;

    private String eventSpecificationSignature;

    private String networkName;

    private String walletContractAddress;

    private String coin;

    public String getId() {
        return transactionHash + "-" + blockHash + "-" + logIndex;
    }
}
