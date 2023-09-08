package org.beykery.eu.test;

import org.beykery.eu.util.EthContractUtil;
import org.junit.jupiter.api.Test;

import java.security.SignatureException;

public class SignTest {

    @Test
    void signTest() throws SignatureException {
        String pri = EthContractUtil.randomPrivate();
        String address = EthContractUtil.address(pri);
        String msg = "test message";
        String signedMessageHex = EthContractUtil.signPrefixedMessage(msg, pri);
        String vAddress = EthContractUtil.getAddressUsedToSignHashedMessage(signedMessageHex, msg);
        assert address.equalsIgnoreCase(vAddress);
    }
}
