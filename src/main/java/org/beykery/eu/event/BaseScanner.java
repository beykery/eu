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
     * start scan
     *
     * @param web3j                web3j
     * @param currentBlockProvider current block provider
     * @param blockInterval        block interval
     * @param pendingInterval      pending tx interval
     * @param pendingParallel      pending parallel
     * @param pendingBatchSize     pending batch size
     * @param events               events for logs
     * @param from                 from block
     * @param minInterval          min interval for log fetch
     * @param sensitivity          sensitivity for interval
     * @param step                 step
     * @param maxRetry             max retry
     * @param retryInterval        retry interval
     * @param logFromTx            log event from transaction
     * @param contracts            contracts for event log
     * @return
     */
    public boolean start(Web3j web3j, CurrentBlockProvider currentBlockProvider, long blockInterval, long pendingInterval, int pendingParallel, int pendingBatchSize, List<Event> events, long from, long minInterval, double sensitivity, long step, int maxRetry, long retryInterval, boolean logFromTx, String... contracts) {
        if (scanner == null) {
            this.from = from;
            this.contracts = contracts;
            scanner = new LogEventScanner(web3j, blockInterval, pendingInterval, pendingParallel, pendingBatchSize, maxRetry, retryInterval, logFromTx, this);
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
