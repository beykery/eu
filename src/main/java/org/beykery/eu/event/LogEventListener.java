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
    void onLogEvents(List<LogEvent> events);

    /**
     * 扫描一段结束
     *
     * @param from
     * @param to
     * @param logSize
     */
    void onOnceScanOver(long from, long to, long logSize);

    /**
     * 达到最大高度
     *
     * @param h
     */
    void onReachHighest(long h);

    /**
     * 事件顺序是否翻转
     *
     * @return
     */
    boolean reverse();
}
