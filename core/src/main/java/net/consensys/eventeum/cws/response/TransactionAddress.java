package net.consensys.eventeum.cws.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAddress {

    private String address;

    private BigInteger amount;

}
