package net.consensys.eventeum.integration.consumer;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import si.mazi.rescu.RestProxyFactory;

@Getter
public class PusherMiddleware {

    private PusherAPI api;

    @Value("${pusher.url}")
    private String url;

    PusherMiddleware() {
        api = RestProxyFactory.createProxy(PusherAPI.class, "http://127.0.0.1:8080/connector-pusher-1.0/rest/pusher/api/v1");
    }

    public static PusherAPI getPusherApi() {
        return new PusherMiddleware().getApi();
    }

}
