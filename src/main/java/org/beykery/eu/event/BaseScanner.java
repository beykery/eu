package org.beykery.eu.event;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.datatypes.Event;
import org.web3j.protocol.geth.Geth;

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
    @Getter
    protected LogEventScanner scanner;

    /**
     * start scan
     *
     * @param web3j                web3j
     * @param pxWeb3j              for pending tx websocket connection
     * @param currentBlockProvider current block provider
     * @param blockInterval        block interval
     * @param pendingInterval      pending tx interval
     * @param pendingMaxDelay      pending max delay
     * @param pendingParallel      pending parallel
     * @param pendingBatchSize     pending batch size
     * @param events               events for logs
     * @param from                 from block
     * @param sensitivity          sensitivity for interval
     * @param step                 step
     * @param maxRetry             max retry
     * @param retryInterval        retry interval
     * @param logFromTx            log event from transaction
     * @param contracts            contracts for event log
     * @return
     */
    public boolean start(
            Geth web3j,
            Geth pxWeb3j,
            CurrentBlockProvider currentBlockProvider,
            long blockInterval,
            long pendingInterval,
            long pendingMaxDelay,
            int pendingParallel,
            int pendingBatchSize,
            List<Event> events,
            long from,
            double sensitivity,
            long step,
            int maxRetry,
            long retryInterval,
            boolean logFromTx,
            String... contracts
    ) {
        if (scanner == null) {
            this.from = from;
            this.contracts = contracts;
            scanner = new LogEventScanner(web3j, pxWeb3j, blockInterval, pendingInterval, pendingMaxDelay, pendingParallel, pendingBatchSize, maxRetry, retryInterval, logFromTx, this);
            return scanner.start(this.from, events, contracts == null || contracts.length == 0 ? Collections.EMPTY_LIST : Arrays.asList(contracts), currentBlockProvider, sensitivity, step);
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

}
