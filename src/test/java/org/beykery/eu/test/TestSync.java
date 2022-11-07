package org.beykery.eu.test;

import org.beykery.eu.event.BaseScanner;
import org.beykery.eu.event.LogEvent;
import org.beykery.eu.util.EthContractUtil;
import org.junit.jupiter.api.Test;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint112;
import org.web3j.protocol.Web3j;
import org.web3j.tuples.generated.Tuple2;
import org.web3j.tx.ReadonlyTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

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

        BaseScanner scanner = new BaseScanner() {
            @Override
            public void onLogEvents(List<LogEvent> events) {
                for (LogEvent e : events) {
                    long block = e.getBlockNumber();
                    long time = e.getBlockTimestamp();
                    long delta = System.currentTimeMillis() - time * 1000;
//                    System.out.println(block + " : " + delta);
                    Uint112 r0 = (Uint112) e.getNonIndexedValues().get(0);
                    Uint112 r1 = (Uint112) e.getNonIndexedValues().get(1);
                    String pairAddress = e.getContract();
                    System.out.println(block + " : " + e.getLogIndex() + " : " + pairAddress + " : " + r0.getValue() + " : " + r1.getValue() + " : " + delta);
//                    System.out.println(this.getScanner().getCurrent() + " : " + block);
                }
            }

            @Override
            public void onOnceScanOver(long from, long to, long logSize) {

            }

            @Override
            public void onReachHighest(long h) {

            }

            @Override
            public void onError(Throwable ex) {

            }

            @Override
            public boolean reverse() {
                return false;
            }
        };
        String contract = "0xEec92107c67C9b6F0875329D7c6E177d864B49a4";
        //String node = "wss://emerald.oasis.dev/ws";
        String node = "https://emerald.oasis.dev";
        //String node = "https://rpc.emerald.oasis.doorgod.io:7545";
        Web3j web3j = EthContractUtil.getWeb3j(node);
        TestContract testContract = TestContract.load(contract, web3j, new ReadonlyTransactionManager(web3j, EthContractUtil.DEFAULT_FROM), new DefaultGasProvider());
        scanner.start(web3j, () -> {
            Tuple2<BigInteger, BigInteger> t2 = testContract.currentBlockInfo().send();
            long[] ret = new long[]{t2.component1().longValue(), t2.component2().longValue()};
//            long latency = System.currentTimeMillis() - ret[1] * 1000;
//            System.out.println("latency : " + latency);
            return ret;
        }, 6000, Arrays.asList(SYNC_EVENT), -1, 300, 0, 1, null);
        Thread.sleep(24 * 3600 * 1000);
    }
}
