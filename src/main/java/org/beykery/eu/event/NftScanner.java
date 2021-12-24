package org.beykery.eu.event;

import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * nft scan
 */
@Slf4j
public abstract class NftScanner extends BaseScanner {

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
     * 开始爬取
     */
    public boolean start(Web3j web3j, long blockInterval, long from, String... contracts) {
        return super.start(web3j, blockInterval, Arrays.asList(TRANSFER_EVENT), from, contracts);
    }

    /**
     * nft event
     *
     * @param events
     */
    protected abstract void onTransferEvents(List<NftTransferEvent> events);

    /**
     * 发现事件
     *
     * @param events
     */
    public void onLogEvents(List<LogEvent> events) {
        List<NftTransferEvent> es = events.stream().map(event -> {
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
                    .blockTimestamp(event.getBlockTimestamp())
                    .transactionHash(event.getTransactionHash())
                    .build();
            return transfer;
        }).collect(Collectors.toList());

        this.onTransferEvents(es);
    }
}
