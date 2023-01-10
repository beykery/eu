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
    void syncTest() throws InterruptedException {

        BaseScanner scanner = new BaseScanner() {
            @Override
            public void onLogEvents(List<LogEvent> events, long from, long to, long current, long currentTime) {
                System.out.println(from + " " + to + " " + current + " " + (current - to));
                System.out.println("elapsed from top block " + (System.currentTimeMillis() - currentTime * 1000));
                for (LogEvent e : events) {
                    long block = e.getBlockNumber();
                    Uint112 r0 = (Uint112) e.getNonIndexedValues().get(0);
                    Uint112 r1 = (Uint112) e.getNonIndexedValues().get(1);
                    String pairAddress = e.getContract();
                    System.out.println(block + " : " + e.getLogIndex() + " : " + pairAddress + " : " + r0.getValue() + " : " + r1.getValue());
                }
            }

            @Override
            public void onOnceScanOver(long from, long to, long current, long currentTime, long logSize) {
            }

            @Override
            public void onReachHighest(long h) {

            }

            @Override
            public void onError(Throwable ex, long from, long to, long current, long currentTime) {

            }

            @Override
            public boolean reverse() {
                return false;
            }
        };
        String contract = "0xDecCfF0273Ec47D913Dd88eAb45d1c00F1be26aF";
        String[] nodes = new String[]{
                "https://rpc.dogechain.dog",
                "https://dogechain.ankr.com",
                "https://rpc-us.dogechain.dog",
                "https://rpc-sg.dogechain.dog",
                "https://rpc01-sg.dogechain.dog",
                "https://rpc02-sg.dogechain.dog",
                "https://rpc03-sg.dogechain.dog",
        };
        Web3j web3j = EthContractUtil.getWeb3j(Arrays.asList(nodes));
        TestContract testContract = TestContract.load(contract, web3j, new ReadonlyTransactionManager(web3j, EthContractUtil.DEFAULT_FROM), new DefaultGasProvider());

        scanner.start(
                web3j,
                () -> {
                    Tuple2<BigInteger, BigInteger> t2 = testContract.currentBlockInfo().send();
                    long[] ret = new long[]{t2.component1().longValue(), t2.component2().longValue()};
                    return ret;
                },
                2000,
                Arrays.asList(SYNC_EVENT),
                -1,
                100,
                0,
                1,
                0,
                100,
                false,
                null
        );
        Thread.sleep(24 * 3600 * 1000);
    }
}
