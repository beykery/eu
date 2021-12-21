package org.beykery.eu.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SwapEvent {
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
     * sender
     */
    private String sender;
    /**
     * to
     */
    private String to;
    /**
     * token0 amount in
     */
    private BigInteger amount0In;
    private BigInteger amount1In;
    private BigInteger amount0Out;
    private BigInteger amount1Out;
}
