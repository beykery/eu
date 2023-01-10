package org.beykery.eu.event;

import java.util.List;

/**
 * listener for log event
 */
public interface LogEventListener {

    /**
     * 发现事件
     *
     * @param events
     */
    void onLogEvents(List<LogEvent> events, long from, long to, long current, long currentTime);

    /**
     * 扫描一段结束
     *
     * @param from
     * @param to
     * @param logSize
     */
    void onOnceScanOver(long from, long to, long current, long currentTime, long logSize);

    /**
     * 达到最大高度
     *
     * @param h
     */
    void onReachHighest(long h);

    /**
     * 错误
     *
     * @param ex
     */
    void onError(Throwable ex, long from, long to, long current, long currentTime);

    /**
     * 事件顺序是否翻转
     *
     * @return
     */
    boolean reverse();
}
