package org.beykery.eu.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * ens registry event
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EnsRegistryEvent {
    /**
     * 交易hash
     */
    private String transactionHash;
    /**
     * 高度
     */
    private long blockNumber;
    /**
     * 时间戳
     */
    private long blockTimestamp;
    /**
     * log发生的index
     */
    private long logIndex;
    /**
     * creator == contract
     */
    private String creatorAddress;
    /**
     * owner
     */
    private String ownerAddress;
    /**
     * contract
     */
    private String contract;
    /**
     * token id
     */
    private BigInteger tokenId;
    /**
     * name
     */
    private String name;
    /**
     * cost
     */
    private BigInteger cost;
    /**
     * 过期事件
     */
    private long expires;
}
