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
     * start
     *
     * @param web3j
     * @param currentBlockProvider
     * @param blockInterval        second
     * @param events
     * @param from
     * @param minInterval
     * @param sensitivity
     * @param step
     * @param contracts
     * @return
     */
    public boolean start(Web3j web3j, CurrentBlockProvider currentBlockProvider, long blockInterval, List<Event> events, long from, long minInterval, double sensitivity, long step, String... contracts) {
        int maxRetry = 3;
        long retryInterval = (long) (blockInterval * 1000.0 / 20);
        return start(web3j, currentBlockProvider, blockInterval, events, from, minInterval, sensitivity, step, maxRetry, retryInterval, contracts);
    }

    /**
     * 启动
     *
     * @param web3j
     * @param currentBlockProvider
     * @param blockInterval
     * @param events
     * @param from
     * @param minInterval
     * @param sensitivity
     * @param step
     * @param maxRetry
     * @param retryInterval
     * @param contracts
     * @return
     */
    public boolean start(Web3j web3j, CurrentBlockProvider currentBlockProvider, long blockInterval, List<Event> events, long from, long minInterval, double sensitivity, long step, int maxRetry, long retryInterval, String... contracts) {
        if (scanner == null) {
            this.from = from;
            this.contracts = contracts;
            scanner = new LogEventScanner(web3j, blockInterval, maxRetry, retryInterval, this);
            return scanner.start(this.from, events, contracts == null || contracts.length <= 0 ? Collections.EMPTY_LIST : Arrays.asList(contracts), currentBlockProvider, minInterval, sensitivity, step);
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
