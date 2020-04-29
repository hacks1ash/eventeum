package net.consensys.eventeum;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Lob;
import java.util.List;
import java.util.Set;

@Document
@Entity
@Data
@ToString
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class Contracts {

    @javax.persistence.Id
    @org.springframework.data.annotation.Id
    private String id;

    @Lob
    @ElementCollection
    private Set<NameContracts> contractAddresses;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NameContracts {

        private String coin;

        private String contractAddress;

        private List<String> forwarders;

    }

}
