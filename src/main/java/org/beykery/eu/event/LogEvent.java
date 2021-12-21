package org.beykery.eu.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;

import java.util.List;

/**
 * log event
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LogEvent {

    /**
     * log event
     */
    private Event event;

    /**
     * 交易hash
     */
    private String transactionHash;
    /**
     * 高度
     */
    private long blockNumber;
    /**
     * log发生的index
     */
    private long logIndex;
    /**
     * contract
     */
    private String contract;
    /**
     * indexed
     */
    private List<Type> indexedValues;
    /**
     * non index
     */
    private List<Type> nonIndexedValues;
}
