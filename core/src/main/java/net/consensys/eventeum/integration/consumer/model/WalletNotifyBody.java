package net.consensys.eventeum.integration.consumer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletNotifyBody {

    String txid;

    List<String> addresses;

    BigInteger blockHeight;


}
