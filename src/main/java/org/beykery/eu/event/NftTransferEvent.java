package org.beykery.eu.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * nft transfer event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NftTransferEvent {
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
     * from
     */
    private String fromAddress;
    /**
     * to
     */
    private String toAddress;
    /**
     * contract
     */
    private String contract;
    /**
     * token id
     */
    private BigInteger tokenId;

}

