package org.beykery.eu.event;

import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;

import java.util.Arrays;

/**
 * swap event
 */
@Slf4j
public abstract class SwapScanner extends BaseScanner {

    public static final Event SWAP_EVENT = new Event("Swap",
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {
            }, new TypeReference<Uint256>() {
            }, new TypeReference<Uint256>() {
            }, new TypeReference<Uint256>() {
            }, new TypeReference<Uint256>() {
            }, new TypeReference<Address>(true) {
            }));

    /**
     * swap event happen
     *
     * @param swapEvent
     */
    protected abstract void onSwapEvent(SwapEvent swapEvent);

    /**
     * log
     *
     * @param event
     */
    @Override
    public void onLogEvent(LogEvent event) {
        Address sender = (Address) event.getIndexedValues().get(0);                 // sender
        Address to = (Address) event.getIndexedValues().get(1);                     // to
        Uint256 amount0In = (Uint256) event.getNonIndexedValues().get(0);           // amount0In
        Uint256 amount1In = (Uint256) event.getNonIndexedValues().get(1);           // amount1In
        Uint256 amount0Out = (Uint256) event.getNonIndexedValues().get(2);          // amount0Out
        Uint256 amount1Out = (Uint256) event.getNonIndexedValues().get(3);          // amount1Out
        SwapEvent swapEvent = SwapEvent.builder()
                .sender(sender.toString().toLowerCase())
                .to(to.toString().toLowerCase())
                .contract(event.getContract().toLowerCase())
                .amount0In(amount0In.getValue())
                .amount1In(amount1In.getValue())
                .amount0Out(amount0Out.getValue())
                .amount1Out(amount1Out.getValue())
                .logIndex(event.getLogIndex())
                .blockNumber(event.getBlockNumber())
                .transactionHash(event.getTransactionHash())
                .build();
        this.onSwapEvent(swapEvent);
    }


    /**
     * start
     *
     * @param web3j
     * @param from
     * @param contracts
     * @return
     */
    public boolean start(Web3j web3j, long blockInterval, long from, String... contracts) {
        return start(web3j, blockInterval, Arrays.asList(SWAP_EVENT), from, contracts);
    }

    @Override
    public void onOnceScanOver(long from, long to, long logSize) {

    }

    @Override
    public void onReachHighest(long h) {

    }
}
