package org.beykery.eu.event;

import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * ens scan
 */
@Slf4j
public abstract class EnsScanner implements LogEventListener {

    /**
     * registered event
     */
    Event event = new Event("NameRegistered",
            Arrays.<org.web3j.abi.TypeReference<?>>asList
                    (
                            new org.web3j.abi.TypeReference<Utf8String>(false) {  // name
                            },
                            new org.web3j.abi.TypeReference<Bytes32>(true) {    // label
                            },
                            new org.web3j.abi.TypeReference<Address>(true) {     // owner
                            },
                            new org.web3j.abi.TypeReference<Uint256>(false) {     // cost
                            },
                            new org.web3j.abi.TypeReference<Uint256>(false) {     // expires
                            }
                    )
    );

    /**
     * contract
     * 9380471
     */
    public static final String contract = "0x283af0b28c62c092c9727f1ee09c02ca627eb7f5";

    /**
     * ens token address
     * 9380410
     */
    public static final String tokenAddress = "0x57f1887a8bf19b14fc0df6fd9b2acc9af147ea85";

    /**
     * ens token min block number
     */

    public static final long ENS_TOKEN_MIN_HEIGHT = 9380410L;
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
    public boolean start(Web3j web3j, long from) {
        if (scanner == null) {
            this.from = Math.max(from, ENS_TOKEN_MIN_HEIGHT);
            scanner = new LogEventScanner(web3j, this);
            return scanner.start(this.from, Arrays.asList(event), Arrays.asList(contract));
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
     * ens event
     *
     * @param registry
     */
    protected abstract void onRegistryEvent(EnsRegistryEvent registry);

    /**
     * 发现事件
     *
     * @param event
     */
    public void onLogEvent(LogEvent event) {
        Bytes32 label = (Bytes32) event.getIndexedValues().get(0);                 // label == token id
        Address owner = (Address) event.getIndexedValues().get(1);                 // owner
        Utf8String name = (Utf8String) event.getNonIndexedValues().get(0);         // name
        Uint256 cost = (Uint256) event.getNonIndexedValues().get(1);               // cost
        Uint256 expires = (Uint256) event.getNonIndexedValues().get(2);            // expires
        BigInteger tokenId = new BigInteger(1, label.getValue());          // token id
        EnsRegistryEvent transfer = EnsRegistryEvent.builder()
                .tokenId(tokenId)
                .contract(event.getContract())
                .creatorAddress(tokenAddress)
                .ownerAddress(owner.getValue().toLowerCase())
                .name(name.getValue())
                .cost(cost.getValue())
                .expires(expires.getValue().longValue())
                .logIndex(event.getLogIndex())
                .blockNumber(event.getBlockNumber())
                .transactionHash(event.getTransactionHash())
                .build();
        onRegistryEvent(transfer);
    }
}
