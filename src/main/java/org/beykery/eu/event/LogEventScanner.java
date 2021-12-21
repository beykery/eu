package org.beykery.eu.event;

import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.datatypes.Event;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthChainId;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.Contract;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * scan log event
 */
@Slf4j
public class LogEventScanner implements Runnable {

    /**
     * eth 主网nft mint事件最早块高
     */
    public static final long MIN_ETH_MAINNET_NFT_MINT_HEIGHT = 937821L;

    /**
     * 监听
     */
    private LogEventListener listener;

    /**
     * web3j
     */
    private Web3j web3j;

    /**
     * scan start
     */
    private volatile boolean scanning;

    /**
     * 事件
     */
    private List<Event> events;

    /**
     * from block
     */
    private long from;

    /**
     * 爬取的合约集合
     */
    private List<String> contracts;

    /**
     * init
     *
     * @param web3j
     */
    public LogEventScanner(Web3j web3j, LogEventListener listener) {
        this.web3j = web3j;
        this.listener = listener;
    }

    /**
     * 开始爬取
     */
    public boolean start(long from, List<Event> events, List<String> contracts) {
        if (!scanning) {
            scanning = true;
            this.events = events;
            this.from = from;
            this.contracts = contracts;
            Thread thread = new Thread(this);
            thread.start();
        }
        return scanning;
    }

    /**
     * stop scan
     */
    public void stop() {
        this.scanning = false;
    }

    /**
     * scanning
     *
     * @return
     */
    public boolean isScanning() {
        return scanning;
    }

    /**
     * @return
     */
    public BigInteger chainId() throws IOException {
        EthChainId cd = web3j.ethChainId().send();
        BigInteger cid = cd.getChainId();
        return cid;
    }

    /**
     * event topic
     *
     * @param event
     * @return
     */
    public static String getTopic(Event event) {
        String encodedEventSignature = EventEncoder.encode(event);
        return encodedEventSignature;
    }

    /**
     * 是否为eth主网
     *
     * @return
     */
    public boolean isEthMainnet() throws IOException {
        return chainId().equals(BigInteger.ONE);
    }

    /**
     * scan
     */
    @Override
    public void run() {
        long delta = 0;  // 预防叔块
        long current = 0; // 当前块高
        try {
            EthBlockNumber n = web3j.ethBlockNumber().send();
            current = n.getBlockNumber().longValue() - delta;
        } catch (Exception ex) {
            scanning = false;
            throw new RuntimeException(ex);
        }
        Map<String, Event> signatures = new HashMap<>();
        events.forEach(item -> {
            String encodedEventSignature = getTopic(item);
            signatures.put(encodedEventSignature, item);
        });
        long step = 1;    // 步长
        long f = from;    // 起始位置
        while (scanning) {
            try {
                long t = Math.min(f + step - 1, current);
                if (f <= t) {
                    EthFilter filter = new EthFilter(
                            DefaultBlockParameter.valueOf(BigInteger.valueOf(f)),
                            DefaultBlockParameter.valueOf(BigInteger.valueOf(t)),
                            (contracts == null || contracts.size() <= 0) ? Collections.EMPTY_LIST : contracts
                    );
                    Set<String> topics = signatures.keySet();
                    filter.addOptionalTopics(topics.toArray(new String[0]));
                    EthLog el = web3j.ethGetLogs(filter).send();
                    long logSize = 0;  // 用来调整步长
                    List<EthLog.LogResult> lr = el == null ? Collections.EMPTY_LIST : el.getLogs();
                    if (lr != null) {
                        List<Log> logs = lr.stream().map(item -> {
                            EthLog.LogObject lo = (EthLog.LogObject) item.get();
                            Log log = lo.get();
                            return log;
                        }).filter(Objects::nonNull).collect(Collectors.toList());
                        log.info("from {} to {} find {} events with step {}", f, t, logs.size(), step);
                        if (logs.size() > 0) {
                            logSize = logs.size();
                            // ParallelStreamSupport.parallelStream(logs, Streams.POOL)
                            logs.stream()
                                    .filter(item -> {
                                        String topic = item.getTopics().get(0);
                                        int size = item.getTopics().size() - 1;
                                        Event event = signatures.get(topic);
                                        return size == event.getIndexedParameters().size();
                                    })
                                    .forEach(item -> {

                                        String topic = item.getTopics().get(0);
                                        Event event = signatures.get(topic);

                                        String tx = item.getTransactionHash().toLowerCase();           // tx hash
                                        BigInteger blockNumber = item.getBlockNumber();                // block number
                                        BigInteger lidx = item.getLogIndex();                          // log index
                                        String contractAddress = item.getAddress().toLowerCase();      // contract address

                                        EventValues values = Contract.staticExtractEventParameters(event, item);

                                        // 通知listener
                                        LogEvent le = LogEvent.builder()
                                                .event(event)
                                                .transactionHash(tx)
                                                .blockNumber(blockNumber.longValue())
                                                .logIndex(lidx.longValue())
                                                .contract(contractAddress)
                                                .indexedValues(values.getIndexedValues())
                                                .nonIndexedValues(values.getNonIndexedValues())
                                                .build();
                                        listener.onLogEvent(le);
                                    });
                        }
                    } else {
                        log.info("from {} to {} find {} events", f, t, 0);
                    }
                    listener.onOnceScanOver(f, t, logSize);
                    f = t + 1;  // to the next loop
                    // step adjust
                    long targetSize = 1024 * 4;
                    long maxStep = 1024;
                    long rate = 60;
                    step = logSize > 0 ? ((step * targetSize / logSize) * rate + step * (100 - rate)) / 100 : step + 1;
                    step = step < 1 ? 1 : (Math.min(step, maxStep));
                } else {
                    log.debug("reach the highest block {}", t);
                    step = 1;
                    listener.onReachHighest(t);
                    current = web3j.ethBlockNumber().send().getBlockNumber().longValue() - delta;
                    Thread.sleep(5 * 1000);
                }
            } catch (Exception ex) {
                log.error("error with event fetch ", ex);
                step = 1;
                try {
                    Thread.sleep(5 * 1000);
                } catch (Exception e) {
                    log.error("sleep error ", e);
                }
            }
        }
    }
}
