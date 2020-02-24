package net.consensys.eventeum.integration.consumer;

import net.consensys.eventeum.integration.consumer.model.WalletNotifyBody;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigInteger;

@Path(value = "/")
@Produces(MediaType.APPLICATION_JSON)
public interface PusherAPI {

    @POST
    @Path(value = "{hostname}/node/wallet-notify/{coin}")
    @Consumes(value = {"application/json"})
    Response sendWalletNotify(@PathParam("hostname") String hostname,
                              @PathParam("coin") String coin,
                              WalletNotifyBody walletNotifyBody);


    @POST
    @Path(value = "{hostname}/node/block-notify/{coin}/{blockHeight}")
    Response sendBlockNotify(@PathParam("hostname") String hostname,
                             @PathParam("coin") String coin,
                             @PathParam("blockHeight") BigInteger blockHeight);

}
