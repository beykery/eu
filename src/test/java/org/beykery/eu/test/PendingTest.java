package org.beykery.eu.test;

import io.reactivex.Flowable;
import org.beykery.eu.util.EthContractUtil;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.geth.JsonRpc2_0Geth;
import org.web3j.protocol.websocket.events.PendingTransactionNotification;

import java.io.IOException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.util.List;

public class PendingTest {
    @Test
    void testPending() throws Exception {
        String url = "wss://rpc.ankr.com/eth/ws/21b405bbb7f73b129d9fea7c2c5f3281fe7e4208c9a692dceff2084489a00875";
        JsonRpc2_0Geth web3j = EthContractUtil.getWeb3j(url);
        Flowable<PendingTransactionNotification> f = web3j.newPendingTransactionsNotifications();
        f.blockingForEach(item -> {
            System.out.println(item);
        });
//        BigInteger fid = EthContractUtil.newPendingTransactionFilterId(web3j);
//        while (true) {
//            List<Transaction> txs = EthContractUtil.pendingTransactions(web3j, fid, 5, 128);
//            System.out.println(txs);
//            Thread.sleep(1000);
//        }
    }
}
