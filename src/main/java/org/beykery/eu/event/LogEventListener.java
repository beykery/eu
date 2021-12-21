package org.beykery.eu.event;

/**
 * listener for log event
 */
public interface LogEventListener {

    /**
     * 发现事件
     *
     * @param event
     */
    void onLogEvent(LogEvent event);

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
}
