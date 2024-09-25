package org.beykery.eu.util;

import org.web3j.protocol.Web3jService;
import org.web3j.protocol.geth.Geth;
import org.web3j.protocol.geth.JsonRpc2_0Geth;

public class EuGeth extends JsonRpc2_0Geth {

    private final Web3jService web3jService;

    public EuGeth(Web3jService web3jService) {
        super(web3jService);
        this.web3jService = web3jService;
    }

    static Geth build(Web3jService web3jService) {
        return new EuGeth(web3jService);
    }

    public Web3jService getWeb3jService() {
        return web3jService;
    }
}
