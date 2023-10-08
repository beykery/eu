package org.beykery.eu.util;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.JsonRpc2_0Web3j;
import org.web3j.protocol.geth.JsonRpc2_0Geth;

import java.util.concurrent.ScheduledExecutorService;

public interface EuWeb3j extends Web3j {

    static JsonRpc2_0Geth build(Web3jService web3jService) {
        return new JsonRpc2_0Geth(web3jService);
    }

}
