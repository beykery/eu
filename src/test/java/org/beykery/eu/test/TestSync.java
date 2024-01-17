package org.beykery.eu.test;

import org.beykery.eu.event.BaseScanner;
import org.beykery.eu.event.LogEvent;
import org.beykery.eu.event.PendingTransaction;
import org.beykery.eu.util.EthContractUtil;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.junit.jupiter.api.Test;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint112;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.geth.Geth;
import org.web3j.tuples.generated.Tuple2;
import org.web3j.tx.ReadonlyTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

import java.io.IOException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.List;

public class TestSync {

    public static final Event SYNC_EVENT = new Event(
            "Sync",
            Arrays.asList(
                    new TypeReference<Uint112>() {
                    },
                    new TypeReference<Uint112>() {
                    }
            )
    );

    @Test
    void syncTest() throws InterruptedException, ConnectException {
        //String contract = "0x67d6859e6C7bee08739A5125EC9CD9cd1291b181";
        String contract = "0x91e8eef3BdBED613cF06E37249769ac571fa83ce";
        String[] nodes = new String[]{
                "https://a.api.s0.t.hmny.io",
                "https://endpoints.omniatech.io/v1/harmony/mainnet/publicrpc",
                "wss://endpoints.omniatech.io/v1/ws/harmony/mainnet-0/7377f57783d54f00b1f85f9e5be9c4f9",
                "wss://endpoints.omniatech.io/v1/ws/harmony/mainnet-0/5a36585dcdb34ceebb1d891ed6c3f50c"
                //"wss://oktc-mainnet.blastapi.io/344c45b5-7ebb-4c27-820a-b93b0a1ab6bf"
                // "https://oktc-mainnet.blastapi.io/344c45b5-7ebb-4c27-820a-b93b0a1ab6bf"
        };
        Geth web3j = EthContractUtil.getWeb3j(nodes[0]);
        TestContract testContract = TestContract.load(contract, web3j, new ReadonlyTransactionManager(web3j, EthContractUtil.DEFAULT_FROM), new DefaultGasProvider());

        BaseScanner scanner = new BaseScanner() {
            @Override
            public void onLogEvents(List<LogEvent> events, long from, long to, long current, long currentTime) {
                System.out.println("log events elapsed from top block " + (System.currentTimeMillis() - currentTime * 1000));
                for (LogEvent e : events) {
                    long block = e.getBlockNumber();
                    Uint112 r0 = (Uint112) e.getNonIndexedValues().get(0);
                    Uint112 r1 = (Uint112) e.getNonIndexedValues().get(1);
                    String pairAddress = e.getContract();
                    System.out.println(block + " : " + e.getLogIndex() + " : " + pairAddress + " : " + r0.getValue() + " : " + r1.getValue());
                }
                System.out.println("---onLogEvents---");
            }

            @Override
            public void onPendingTransactions(List<PendingTransaction> txs, long current, long currentTime) {
                System.out.println("pending txs : " + txs.size());
                long elapsed = System.currentTimeMillis() - currentTime * 1000;
                System.out.println("pending txs elapsed: " + elapsed);
                System.out.println("---onPendingTransactions---");
            }

            @Override
            public boolean onPendingTransactionHash(String hash, long current, long currentTime) {
                System.out.println(hash);
                return false;
            }

            @Override
            public void onPendingError(Throwable ex, long current, long currentTime) {
                ex.printStackTrace();
            }

            @Override
            public void onOnceScanOver(long from, long to, long current, long currentTime, long logSize) {
            }

            @Override
            public void onReachHighest(long h) {

            }

            @Override
            public void onError(Throwable ex, long from, long to, long current, long currentTime) {
                ex.printStackTrace();
            }

            @Override
            public void onWebsocketBroken(WebsocketNotConnectedException ex, long current, long currentTime) {
                try {
                    ex.printStackTrace();
                    Geth web3j = EthContractUtil.getWeb3j(nodes[1]);
                    this.scanner.reconnect(web3j);
                } catch (Exception exception) {
                    System.out.println(exception);
                }
            }

            @Override
            public boolean reverse() {
                return false;
            }
        };

        scanner.start(
                web3j,
                EthContractUtil.getWeb3j("wss://go.getblock.io/268c00869f03478083024101330bef2c"),
                () -> {
                    Tuple2<BigInteger, BigInteger> t2 = testContract.currentBlockInfo().send();
                    long[] ret = new long[]{t2.component1().longValue(), t2.component2().longValue()};
                    return ret;
                },
                2000,
                100,
                4000,
                3,
                100,
                Arrays.asList(SYNC_EVENT),
                -1,
                10,
                0,
                2,
                0,
                false,
                null
        );
        Thread.sleep(24 * 3600 * 1000);
    }

    @Test
    void testCode() throws IOException {
        String contract = "0x91e8eef3BdBED613cF06E37249769ac571fa83ce";
        //String url = "https://endpoints.omniatech.io/v1/harmony/mainnet/publicrpc";
        String url = "https://a.api.s0.t.hmny.io";
        Geth web3j = EthContractUtil.getWeb3j(url);
        String code = EthContractUtil.getCode(web3j, contract);
        System.out.println(code);
    }
}
