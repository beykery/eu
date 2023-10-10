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
import java.util.List;

public class PendingTest {
    @Test
    void testPending() throws Exception {
        //String url = "wss://eth-mainnet.blastapi.io/344c45b5-7ebb-4c27-820a-b93b0a1ab6bf";
        //String url = "https://eth-mainnet.blastapi.io/344c45b5-7ebb-4c27-820a-b93b0a1ab6bf";
        //String url = "wss://astar.blastapi.io/344c45b5-7ebb-4c27-820a-b93b0a1ab6bf";
        //String url = "https://astar.blastapi.io/344c45b5-7ebb-4c27-820a-b93b0a1ab6bf";
        String url = "wss://rpc.ankr.com/eth/ws/21b405bbb7f73b129d9fea7c2c5f3281fe7e4208c9a692dceff2084489a00875";
        Geth web3j = EthContractUtil.getWeb3j(url);
//        long blk = web3j.ethBlockNumber().send().getBlockNumber().longValue();
//        System.out.println(blk);
//        long id = EthContractUtil.chainId(web3j);
//        System.out.println(id);
        try {
            Flowable<PendingTransactionNotification> f = web3j.newPendingTransactionsNotifications();
            f.blockingForEach(item -> {
                System.out.println(item.getParams().getResult());
            });
        } catch (Exception ex) {
            System.out.println(ex);
        }

//        BigInteger fid = EthContractUtil.newPendingTransactionFilterId(web3j);
//        while (true) {
//            List<Transaction> txs = EthContractUtil.pendingTransactions(web3j, fid, 5, 128);
//            System.out.println(txs);
//            Thread.sleep(1000);
//        }
    }
}
