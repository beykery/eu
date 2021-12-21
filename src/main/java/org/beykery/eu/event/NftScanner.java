package org.beykery.eu.event;

import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

/**
 * nft scan
 */
@Slf4j
public abstract class NftScanner implements LogEventListener {

    /**
     * transfer
     */
    private static final Event TRANSFER_EVENT = new Event("Transfer",
            Arrays.asList(
                    new TypeReference<Address>(true) {   // from
                    },
                    new TypeReference<Address>(true) {   // to
                    },
                    new TypeReference<Uint256>(true) {   // token id
                    }
            )
    );

    /**
     * contract
     */
    private String[] contracts;

    /**
     * from
     */
    private long from;

    /**
     * scan
     */
    private LogEventScanner scanner;

    /**
     * 开始爬取
     */
    public boolean start(Web3j web3j, long from, String... contracts) {
        if (scanner == null) {
            this.from = Math.max(from, LogEventScanner.MIN_ETH_MAINNET_NFT_MINT_HEIGHT);
            this.contracts = contracts;
            scanner = new LogEventScanner(web3j, this);
            return scanner.start(this.from, Arrays.asList(TRANSFER_EVENT), contracts == null || contracts.length <= 0 ? Collections.EMPTY_LIST : Arrays.asList(contracts));
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
     * nft event
     *
     * @param transfer
     */
    protected abstract void onTransferEvent(NftTransferEvent transfer);

    /**
     * 发现事件
     *
     * @param event
     */
    public void onLogEvent(LogEvent event) {
        Address fa = (Address) event.getIndexedValues().get(0);
        Address ta = (Address) event.getIndexedValues().get(1);
        BigInteger tokenId = ((Uint256) event.getIndexedValues().get(2)).getValue();
        NftTransferEvent transfer = NftTransferEvent.builder()
                .tokenId(tokenId)
                .contract(event.getContract())
                .toAddress(ta.getValue().toLowerCase())
                .fromAddress(fa.getValue().toLowerCase())
                .logIndex(event.getLogIndex())
                .blockNumber(event.getBlockNumber())
                .transactionHash(event.getTransactionHash())
                .build();
        onTransferEvent(transfer);
    }
}
