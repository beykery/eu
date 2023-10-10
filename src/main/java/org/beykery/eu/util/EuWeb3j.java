package org.beykery.eu.util;

import org.web3j.protocol.Web3jService;
import org.web3j.protocol.geth.Geth;
import org.web3j.protocol.geth.JsonRpc2_0Geth;

public interface EuWeb3j extends Geth {

    static Geth build(Web3jService web3jService) {
        return new JsonRpc2_0Geth(web3jService);
    }

}
