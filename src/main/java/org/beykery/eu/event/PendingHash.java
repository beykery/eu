package org.beykery.eu.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PendingHash {
    /**
     * hash
     */
    private String hash;
    /**
     * time of appearance
     */
    private long time;
    /**
     * from websocket
     */
    private boolean fromWs;
}
