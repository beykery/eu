package org.beykery.eu.event;

import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.datatypes.Event;
import org.web3j.protocol.Web3j;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
public abstract class BaseScanner implements LogEventListener {

    /**
     * contract
     */
    protected String[] contracts;

    /**
     * from
     */
    protected long from;

    /**
     * scan
     */
    protected LogEventScanner scanner;

    /**
     * 开始爬取
     */
    public boolean start(Web3j web3j, long blockInterval, List<Event> events, long from, String... contracts) {
        return start(web3j, null, blockInterval, events, from, contracts);
    }

    /**
     * 开始爬取
     *
     * @param web3j
     * @param currentBlockProvider
     * @param blockInterval
     * @param events
     * @param from
     * @param contracts
     * @return
     */
    public boolean start(Web3j web3j, CurrentBlockProvider currentBlockProvider, long blockInterval, List<Event> events, long from, String... contracts) {
        return start(web3j, currentBlockProvider, blockInterval, events, from, 0, contracts);
    }

    /**
     * 开始爬取
     *
     * @param web3j
     * @param currentBlockProvider
     * @param blockInterval
     * @param events
     * @param from
     * @param minInterval
     * @param contracts
     * @return
     */
    public boolean start(Web3j web3j, CurrentBlockProvider currentBlockProvider, long blockInterval, List<Event> events, long from, long minInterval, String... contracts) {
        return start(web3j, currentBlockProvider, blockInterval, events, from, minInterval, 0, contracts);
    }

    /**
     * 开始爬取
     *
     * @param web3j
     * @param currentBlockProvider
     * @param blockInterval
     * @param events
     * @param from
     * @param minInterval
     * @param contracts
     * @return
     */
    public boolean start(Web3j web3j, CurrentBlockProvider currentBlockProvider, long blockInterval, List<Event> events, long from, long minInterval, double sensitivity, String... contracts) {
        if (scanner == null) {
            this.from = from;
            this.contracts = contracts;
            scanner = new LogEventScanner(web3j, blockInterval, this);
            return scanner.start(this.from, events, contracts == null || contracts.length <= 0 ? Collections.EMPTY_LIST : Arrays.asList(contracts), currentBlockProvider, minInterval, sensitivity);
        }
        return false;
    }

    /**
     * stop scan
     */
    public void stop() {
        scanner.stop();
    }

    /**
     * scanning
     *
     * @return
     */
    public boolean isScanning() {
        return scanner != null && scanner.isScanning();
    }

    /**
     * the inner scanner
     *
     * @return
     */
    public LogEventScanner getScanner() {
        return this.scanner;
    }
}
