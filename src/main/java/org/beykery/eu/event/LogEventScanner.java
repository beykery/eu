package org.beykery.eu.event;

import io.reactivex.Flowable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.beykery.eu.util.EthContractUtil;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.web3j.abi.datatypes.Event;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthChainId;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.geth.Geth;
import org.web3j.protocol.websocket.events.PendingTransactionNotification;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;

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
    private final LogEventListener listener;

    /**
     * web3j
     */
    private Geth web3j;

    /**
     * for pending transactions
     */
    private Geth pxWeb3j;

    /**
     * scan start
     * -- GETTER --
     * scanning
     *
     * @return
     */
    @Getter
    private volatile boolean scanning;

    /**
     * pending
     */
    private volatile boolean pending;
    /**
     * queue for hash
     */
    private final LinkedBlockingDeque<PendingHash> pendingQueue;

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
    private final boolean logFromTx;

    /**
     * 出块间隔(ms)
     */
    private final long blockInterval;

    /**
     * pending interval
     */
    private final long pendingInterval;

    /**
     * pending max delay, greater than 0 and no longer than blockInterval
     */
    private final long pendingMaxDelay;

    /**
     * pending tx parallel
     */
    private final int pendingParallel;

    /**
     * pending tx batch size
     */
    private final int pendingBatchSize;

    /**
     * 当前高
     * -- GETTER --
     * 当前块高
     *
     * @return
     */
    @Getter
    private long current;

    /**
     * 当前高度的时间(second)
     * -- GETTER --
     * 当前块高时间(second)
     *
     * @return
     */
    @Getter
    private long currentTime;

    /**
     * 块高提供者
     */
    private CurrentBlockProvider currentBlockProvider;

    /**
     * 统计平均出块间隔，计算滑动平均值(ms)
     * -- GETTER --
     * 平均出块间隔(ms)
     *
     * @return
     */
    @Getter
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
    private final int maxRetry;

    /**
     * retry sleep (ms)
     */
    private final long retryInterval;

    /**
     * log event scanner
     *
     * @param web3j
     * @param blockInterval
     * @param pendingInterval
     * @param pendingParallel
     * @param pendingBatchSize
     * @param maxRetry
     * @param retryInterval
     * @param logFromTx
     * @param listener
     */
    public LogEventScanner(
            Geth web3j,
            Geth pxWeb3j,
            long blockInterval,
            long pendingInterval,
            long pendingMaxDelay,
            int pendingParallel,
            int pendingBatchSize,
            int maxRetry,
            long retryInterval,
            boolean logFromTx,
            LogEventListener listener
    ) {
        this.web3j = web3j;
        this.pxWeb3j = pxWeb3j == null ? web3j : pxWeb3j;
        this.blockInterval = blockInterval;
        this.listener = listener;
        this.maxRetry = maxRetry;
        this.retryInterval = retryInterval;
        this.logFromTx = logFromTx;
        this.pendingInterval = pendingInterval;
        this.pendingMaxDelay = pendingMaxDelay <= 0 ? blockInterval : pendingMaxDelay;
        this.pendingParallel = pendingParallel;
        this.pendingBatchSize = pendingBatchSize;
        this.pendingQueue = new LinkedBlockingDeque<>();
    }

    /**
     * start
     *
     * @param from
     * @param events
     * @param contracts
     * @param currentBlockProvider
     * @param sensitivity
     * @param step
     * @return
     */
    public boolean start(
            long from,
            List<Event> events,
            List<String> contracts,
            CurrentBlockProvider currentBlockProvider,
            double sensitivity,
            long step
    ) {
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
            // 尝试启动pending
            if (pendingInterval > 0) {
                startPending();
            }
            Thread thread = new Thread(this);
            thread.setName("thread - event");
            thread.start();
        }
        return scanning;
    }

    /**
     * lock for pending txs
     */
    private static final Object PX_LOCK = new Object();

    /**
     * 尝试启动pending
     */
    public void startPending() {
        if (!pending) {
            pending = true;
            Runnable run = () -> {
                try {
                    Flowable<PendingTransactionNotification> f = pxWeb3j.newPendingTransactionsNotifications();
                    f.blockingForEach(item -> {
                        String hash = item.getParams().getResult();
                        boolean processed = this.listener.onPendingTransactionHash(hash, this.current, this.currentTime);
                        if (!processed) {
                            pendingQueue.offer(new PendingHash(hash, System.currentTimeMillis(), true));
                            synchronized (PX_LOCK) {
                                PX_LOCK.notifyAll();
                            }
                        }
                    });
                } catch (WebsocketNotConnectedException ex) {
                    pending = false;
                    log.error("websocket connection broken", ex);
                    this.listener.onWebsocketBroken(ex, current, currentTime);
                } catch (Throwable ex) {
                    pending = false;
                    this.listener.onPendingError(ex, this.current, this.currentTime);
                }
                log.error("pending quited. ");
            };
            Thread thread = new Thread(run);
            thread.setName("thread - pending");
            thread.start();
        }
    }

    /**
     * 停止pending
     */
    public void stopPending() {
        this.pending = false;
    }

    /**
     * stop scan
     */
    public void stop() {
        this.scanning = false;
        this.pending = false;
    }

    /**
     * @return
     */
    public BigInteger chainId() throws IOException {
        EthChainId cd = web3j.ethChainId().send();
        return cd.getChainId();
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
                            retry++;
                            if (retry <= maxRetry) {
                                try {
                                    Thread.sleep(retryInterval);
                                } catch (Exception x) {
                                }
                            }
                        } else if (retry > 0) { // retry 后成功
                            log.info("fetch {} logs success from {} to {} with {} retry", les.size(), f, t, retry);
                        }
                    } catch (Throwable ex) {
                        retry++;
                        maxRetry = Math.max(this.maxRetry, 1);
                        log.error("fetch logs error from {} to {} with {} retry", f, t, retry);
                        listener.onError(ex, f, t, current, currentTime);
                        step = 1;
                        if (retry <= maxRetry) {
                            try {
                                Thread.sleep(retryInterval);
                            } catch (Exception x) {
                            }
                        }
                    }
                }
                les = les == null ? Collections.EMPTY_LIST : les;
                long logSize = les.size();  // 用来调整步长
                if (logSize > 0 && listener.reverse()) {
                    Collections.reverse(les);
                }

                // 通知
                listener.onLogEvents(les, f, t, current, currentTime);
                listener.onOnceScanOver(f, t, current, currentTime, logSize);

                f = t + 1;

                // step adjust
                long targetSize = 1024 * 4;
                long maxStep = 1024;
                long rate = 60;
                step = logSize > 0 ? ((step * targetSize / logSize) * rate + step * (100 - rate)) / 100 : step + 1;
                step = step < 1 ? 1 : (Math.min(step, maxStep));
            }
            // reach 't' height
            else {
                listener.onReachHighest(t);
                step = 1;
            }
            long next = currentTime * 1000 + blockInterval; // 下次出块时间
            if (pendingInterval >= 0) {
                do {
                    // pending tx
                    List<PendingTransaction> pendingTxs = pendingTxs();
                    if (pendingTxs != null && !pendingTxs.isEmpty()) {
                        listener.onPendingTransactions(pendingTxs, current, currentTime);
                    }
                    long now = System.currentTimeMillis();
                    if (now < next) {
                        long maxSleep = next - now;
                        synchronized (PX_LOCK) {
                            try {
                                PX_LOCK.wait(maxSleep);
                            } catch (Throwable th) {
                                log.error("PX_LOCK wait error", th);
                            }
                        }
                    }
                } while (System.currentTimeMillis() - next + blockInterval < pendingMaxDelay);
            }
            // 等待下一个块到来
            long delta = next - System.currentTimeMillis();
            if (delta > 0) {
                synchronized (PX_LOCK) {
                    try {
                        PX_LOCK.wait(delta);
                    } catch (Throwable th) {
                        log.error("PX_LOCK wait error", th);
                    }
                }
            }
            // 求当前最高块
            try {
                long[] c = this.currentBlockProvider.currentBlockNumberAndTimestamp();
                if (c[0] > current) {
                    this.averageBlockInterval = (long) (this.averageBlockInterval * (1 - sensitivity) + 1000.0 * (c[1] - currentTime) / (c[0] - current) * sensitivity);
                    current = c[0];
                    currentTime = c[1];
                } else if (c[0] < current) {
                    log.debug("block {} less than current block {}, ignore it .", c[0], current);
                    Thread.sleep(1);
                }
            } catch (WebsocketNotConnectedException ex) {
                log.error("websocket connection broken", ex);
                this.listener.onWebsocketBroken(ex, current, currentTime);
            } catch (Throwable e) {
                log.error("fetch the current block number and timestamp failed", e);
            }
        }
    }

    private BigInteger fid;

    /**
     * pending txs
     *
     * @return
     */
    private List<PendingTransaction> pendingTxs() {
        if (pending) {
            Map<String, PendingHash> hash = new HashMap<>();
            while (!pendingQueue.isEmpty()) {
                PendingHash ph = pendingQueue.remove();
                hash.put(ph.getHash(), ph);
            }
            if (!hash.isEmpty()) {
                List<org.web3j.protocol.core.methods.response.Transaction> txs = EthContractUtil.pendingTransactions(pxWeb3j, new ArrayList<>(hash.keySet()), pendingParallel <= 0 ? 3 : pendingParallel, 1);
                return txs.stream().map(item -> {
                    PendingHash ph = hash.get(item.getHash());
                    return new PendingTransaction(item, ph.getTime(), ph.isFromWs());
                }).toList();
            } else {
                return Collections.EMPTY_LIST;
            }
        } else {
            try {
                if (fid == null) {
                    fid = EthContractUtil.newPendingTransactionFilterId(web3j);
                }
                return EthContractUtil.pendingTransactions(web3j, fid, pendingParallel <= 0 ? 3 : pendingParallel, pendingBatchSize <= 0 ? 50 : pendingBatchSize);
            } catch (Exception ex) {
                log.error("fetch pending transactions error", ex);
                fid = null;
                return Collections.EMPTY_LIST;
            }
        }
    }

    /**
     * 重新连接
     */
    public void reconnect(Geth web3j) {
        this.pxWeb3j = web3j;
        this.startPending();
    }
}
