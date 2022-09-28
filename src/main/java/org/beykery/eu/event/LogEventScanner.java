package org.beykery.eu.event;

import lombok.extern.slf4j.Slf4j;
import org.beykery.eu.util.EthContractUtil;
import org.web3j.abi.datatypes.Event;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthChainId;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

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
     * 是否从tx中分析log
     */
    private boolean logFromTx;

    /**
     * 出块间隔(second)
     */
    private long blockInterval;

    /**
     * 最新块的最小获取间隔(ms)
     */
    private long minInterval;

    /**
     * 当前高
     */
    private long current;

    /**
     * 当前高度的时间(second)
     */
    private long currentTime;

    /**
     * 块高提供者
     */
    private CurrentBlockProvider currentBlockProvider;

    /**
     * 统计平均出块间隔，计算滑动平均值(ms)
     */
    private long averageBlockInterval;

    /**
     * 敏感因子，新块间隔在均值里的比重
     */
    private double sensitivity;

    /**
     * 固定步长
     */
    private long step;

    /**
     * 最多重试次数
     */
    private int maxRetry;

    /**
     * retry sleep (ms)
     */
    private long retryInterval;

    /**
     * init
     *
     * @param web3j
     */
    public LogEventScanner(Web3j web3j, long blockInterval, LogEventListener listener) {
        this.web3j = web3j;
        this.blockInterval = blockInterval;
        this.listener = listener;
    }

    public LogEventScanner(Web3j web3j, long blockInterval, int maxRetry, long retryInterval, LogEventListener listener) {
        this.web3j = web3j;
        this.blockInterval = blockInterval;
        this.listener = listener;
        this.maxRetry = maxRetry;
        this.retryInterval = retryInterval;
    }

    /**
     * scanner
     *
     * @param web3j
     * @param blockInterval
     * @param maxRetry
     * @param retryInterval
     * @param logFromTx
     * @param listener
     */
    public LogEventScanner(Web3j web3j, long blockInterval, int maxRetry, long retryInterval, boolean logFromTx, LogEventListener listener) {
        this.web3j = web3j;
        this.blockInterval = blockInterval;
        this.listener = listener;
        this.maxRetry = maxRetry;
        this.retryInterval = retryInterval;
        this.logFromTx = logFromTx;
    }

    /**
     * 开始爬取
     */
    public boolean start(long from, List<Event> events, List<String> contracts) {
        return start(from, events, contracts, null);
    }

    /**
     * 爬取
     *
     * @param from
     * @param events
     * @param contracts
     * @param currentBlockProvider
     * @return
     */
    public boolean start(long from, List<Event> events, List<String> contracts, CurrentBlockProvider currentBlockProvider) {
        return start(from, events, contracts, currentBlockProvider, 0);
    }

    /**
     * 开始爬取
     *
     * @param from
     * @param events
     * @param contracts
     * @param currentBlockProvider
     * @param minInterval
     * @return
     */
    public boolean start(long from, List<Event> events, List<String> contracts, CurrentBlockProvider currentBlockProvider, long minInterval) {
        return start(from, events, contracts, currentBlockProvider, minInterval, 0);
    }

    /**
     * 开始爬取
     *
     * @param from
     * @param events
     * @param contracts
     * @param currentBlockProvider
     * @param minInterval
     * @param sensitivity
     * @return
     */
    public boolean start(long from, List<Event> events, List<String> contracts, CurrentBlockProvider currentBlockProvider, long minInterval, double sensitivity) {
        return start(from, events, contracts, currentBlockProvider, minInterval, sensitivity, step);
    }

    /**
     * start
     *
     * @param from
     * @param events
     * @param contracts
     * @param currentBlockProvider
     * @param minInterval
     * @param step
     * @return
     */
    public boolean start(long from, List<Event> events, List<String> contracts, CurrentBlockProvider currentBlockProvider, long minInterval, long step) {
        return start(from, events, contracts, currentBlockProvider, minInterval, 0, step);
    }

    /**
     * start
     *
     * @param from
     * @param events
     * @param contracts
     * @param currentBlockProvider
     * @param minInterval
     * @param sensitivity
     * @param step
     * @return
     */
    public boolean start(long from, List<Event> events, List<String> contracts, CurrentBlockProvider currentBlockProvider, long minInterval, double sensitivity, long step) {
        if (currentBlockProvider == null) {
            currentBlockProvider = () -> {
                EthBlock block = web3j.ethGetBlockByNumber(DefaultBlockParameterName.fromString("latest"), false).send();
                long current = block.getBlock().getNumber().longValue();
                long currentTime = block.getBlock().getTimestamp().longValue();
                return new long[]{current, currentTime};
            };
        }
        this.currentBlockProvider = currentBlockProvider;
        this.sensitivity = sensitivity <= 0 || sensitivity >= 1 ? 1.0 / 4 : sensitivity;
        this.averageBlockInterval = blockInterval * 1000;
        if (!scanning) {
            scanning = true;
            this.events = events;
            this.from = from;
            this.step = step;
            this.contracts = contracts;
            this.minInterval = minInterval;
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
        try {
            long[] c = this.currentBlockProvider.currentBlockNumberAndTimestamp();
            current = c[0];
            currentTime = c[1];
        } catch (Exception ex) {
            scanning = false;
            throw new RuntimeException(ex);
        }
        final long minInterval = this.minInterval == 0 ? 1000 * blockInterval / 3 : this.minInterval; // 最小间隔
        long latest = 0;
        from = from < 0 ? current : from; // from
        long step = 1;    // 步长
        long f = from;    // 起始位置
        while (scanning) {
            if (this.step > 0) {
                step = this.step;
            }
            long t = Math.min(f + step - 1, current);
            if (f <= t) {
                List<LogEvent> les = null;
                int retry = 0;
                int maxRetry = this.maxRetry;
                while ((les == null || les.isEmpty()) && (retry <= 0 || retry <= maxRetry)) {
                    try {
                        les = EthContractUtil.getLogEvents(web3j, f, t, events, contracts, logFromTx);
                        if (les.isEmpty()) {
                            maxRetry = 3;
                            retry++;
                        } else if (retry > 0) { // retry 后成功
                            log.info("fetch {} logs success from {} to {} with {} retry", les.size(), f, t, retry);
                        }
                    } catch (Throwable ex) {
                        log.error("fetch logs error from {} to {} with {} retry", f, t, retry);
                        listener.onError(ex);
                        step = 1;
                        try {
                            Thread.sleep(retryInterval);
                            retry++;
                        } catch (Exception x) {
                        }
                    }
                }
                les = les == null ? Collections.EMPTY_LIST : les;
                long logSize = les.size();  // 用来调整步长
                if (logSize > 0 && listener.reverse()) {
                    Collections.reverse(les);
                }
                listener.onLogEvents(les);
                listener.onOnceScanOver(f, t, logSize);
                f = t + 1;  // to the next loop
                // step adjust
                long targetSize = 1024 * 4;
                long maxStep = 1024;
                long rate = 60;
                step = logSize > 0 ? ((step * targetSize / logSize) * rate + step * (100 - rate)) / 100 : step + 1;
                step = step < 1 ? 1 : (Math.min(step, maxStep));
            }
            // reach 't' height
            else {
                log.debug("reach the highest block {}", t);
                step = 1;
                listener.onReachHighest(t);
                long next = currentTime + blockInterval;
                long delta = next * 1000 - System.currentTimeMillis();
                if (delta > 0) {
                    log.debug("sleep for the next filter with {} milliseconds", delta);
                    try {
                        Thread.sleep(delta);
                    } catch (Exception x) {
                    }
                }
                long now = System.currentTimeMillis();
                if (latest > 0 && now - latest < minInterval) {
                    try {
                        Thread.sleep(minInterval - now + latest);
                    } catch (Exception x) {
                    }
                }
                try {
                    long[] c = this.currentBlockProvider.currentBlockNumberAndTimestamp();
                    if (c[0] == current + 1) {
                        this.averageBlockInterval = (long) (this.averageBlockInterval * (1 - sensitivity) + (c[1] - currentTime) * 1000 * sensitivity);
                    }
                    current = c[0];
                    currentTime = c[1];
                } catch (Exception ex) {
                    log.error("fetch the current block number and timestamp failed");
                }
                latest = System.currentTimeMillis();
            }
        }
    }

    /**
     * 当前块高
     *
     * @return
     */
    public long getCurrent() {
        return current;
    }

    /**
     * 当前块高时间
     *
     * @return
     */
    public long getCurrentTime() {
        return currentTime;
    }

    /**
     * 平均出块间隔
     *
     * @return
     */
    public long getAverageBlockInterval() {
        return averageBlockInterval;
    }
}
