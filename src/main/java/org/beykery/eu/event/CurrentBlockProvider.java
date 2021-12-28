package org.beykery.eu.event;

/**
 * current block provider
 */
public interface CurrentBlockProvider {

    /**
     * 当前块高和时间戳
     * <p>
     * 0: block number ;
     * 1: block timestamp
     *
     * @return
     */
    long[] currentBlockNumberAndTimestamp() throws Exception;
}
