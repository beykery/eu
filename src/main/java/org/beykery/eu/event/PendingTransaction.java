package org.beykery.eu.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PendingTransaction {
    /**
     * transaction
     */
    private org.web3j.protocol.core.methods.response.Transaction transaction;
    /**
     * time of appearance
     */
    private long time;
    /**
     * from websocket
     */
    private boolean fromWs;
}
