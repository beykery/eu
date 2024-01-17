package org.beykery.eu.test;

import io.reactivex.Flowable;
import org.beykery.eu.util.EthContractUtil;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.geth.Geth;
import org.web3j.protocol.geth.JsonRpc2_0Geth;
import org.web3j.protocol.websocket.events.PendingTransactionNotification;

import java.io.IOException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.List;

public class PendingTest {
    @Test
    void testPending() throws Exception {
        //String url = "wss://eth-mainnet.blastapi.io/344c45b5-7ebb-4c27-820a-b93b0a1ab6bf";
        String url = "wss://go.getblock.io/268c00869f03478083024101330bef2c";
        Geth web3j = EthContractUtil.getWeb3j(url);
        long blk = web3j.ethBlockNumber().send().getBlockNumber().longValue();
        System.out.println(blk);
        long id = EthContractUtil.chainId(web3j);
        System.out.println(id);
        Thread thread = new Thread(() -> {
            try {
                Flowable<PendingTransactionNotification> f = web3j.newPendingTransactionsNotifications();
                f.blockingForEach(item -> {
                    System.out.println(item.getParams().getResult());
                    List<org.web3j.protocol.core.methods.response.Transaction> txs = EthContractUtil.pendingTransactions(web3j, Arrays.asList(item.getParams().getResult()), 3, 1);
                    if (!txs.isEmpty()) {
                        System.out.println(txs.stream().map(org.web3j.protocol.core.methods.response.Transaction::getHash).toList());
                    } else {
                        System.out.println("[]");
                    }
                });
            } catch (Exception ex) {
                System.out.println(ex);
            }
        });
        thread.start();
        //
        Thread.sleep(5000);
        id = EthContractUtil.chainId(web3j);
        System.out.println(id);
        Thread.sleep(5000);
        blk = web3j.ethBlockNumber().send().getBlockNumber().longValue();
        System.out.println(blk);
        Thread.sleep(24 * 3600 * 1000);
//        BigInteger fid = EthContractUtil.newPendingTransactionFilterId(web3j);
//        while (true) {
//            List<Transaction> txs = EthContractUtil.pendingTransactions(web3j, fid, 5, 128);
//            System.out.println(txs);
//            Thread.sleep(1000);
//        }
    }
}
