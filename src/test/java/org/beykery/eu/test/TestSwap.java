package org.beykery.eu.test;

import lombok.extern.slf4j.Slf4j;
import org.beykery.eu.event.SwapEvent;
import org.beykery.eu.event.SwapScanner;
import org.beykery.eu.util.EthContractUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;

import java.net.ConnectException;

@Slf4j
public class TestSwap {

    UniSwapScanner scanner;

    @BeforeEach
    void setup() {
        scanner = new UniSwapScanner();
    }

    @Test
    void start() throws InterruptedException, ConnectException {
//        String node = "https://mainnet.infura.io/v3/9aa3d95b3bc440fa88ea12eaa4456161";
        String node = "wss://mainnet.infura.io/ws/v3/9aa3d95b3bc440fa88ea12eaa4456161";
        Web3j web3j = EthContractUtil.getWeb3j(node);
        long from = 13862733-1000;
        scanner.start(web3j, 15, from, null);
        Thread.sleep(1000 * 60 * 60);
    }

    @Test
    void testHeco() throws InterruptedException, ConnectException {
        //String node = "https://http-mainnet.hecochain.com";
        //String node = "wss://pub001.hg.network/ws";
        String node = "https://pub001.hg.network/rpc";
        Web3j web3j = EthContractUtil.getWeb3j(node);
        long from = 11128695-1000;
        scanner.start(web3j, 3, from, null);
        Thread.sleep(1000 * 60 * 60);
    }

    @Test
    void testBsc() throws InterruptedException, ConnectException {
        String node = "https://bsc-dataseed.binance.org/";
       // String node = "wss://bsc-ws-node.nariox.org:443";
//        String node = "wss://speedy-nodes-nyc.moralis.io/1a2b3c4d5e6f1a2b3c4d5e6f/bsc/mainnet/ws";
        Web3j web3j = EthContractUtil.getWeb3j(node);
        long from = 13731524-1000;
        scanner.start(web3j, 3, from, null);
        Thread.sleep(1000 * 60 * 60);
    }

    class UniSwapScanner extends SwapScanner {

        @Override
        protected void onSwapEvent(SwapEvent swapEvent) {
            System.out.println(swapEvent.toString());
        }

        @Override
        public boolean reverse() {
            return true;
        }
    }
}
