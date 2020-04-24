package net.consensys.eventeum.integration.consumer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Webhook {

    private String hostname;

    private String coin;

    private String txid;

    private List<String> addresses;

    private BigInteger blockHeight;

}
