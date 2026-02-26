package com.spotspread.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ManagedWebSocket {

    private static final Logger log = LoggerFactory.getLogger(ManagedWebSocket.class);
    private static final long INITIAL_RECONNECT_DELAY_MS = 1_000;
    private static final long MAX_RECONNECT_DELAY_MS = 60_000;
    private static final double RECONNECT_BACKOFF_MULTIPLIER = 2.0;

    private final String exchangeName;
    private final URI uri;
    private final ExchangeWebSocketHandler handler;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-reconnect");
        t.setDaemon(true);
        return t;
    });

    private volatile WebSocketConnection connection;
    private volatile long nextReconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;
    private volatile ScheduledFuture<?> reconnectFuture;
    private volatile ScheduledFuture<?> heartbeatFuture;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong lastMessageTimeMs = new AtomicLong(0);
    private volatile long connectionOpenTimeMs = 0;

    public ManagedWebSocket(String exchangeName, URI uri, ExchangeWebSocketHandler handler) {
        this.exchangeName = exchangeName;
        this.uri = uri;
        this.handler = handler;
    }

    public void connect() {
        if (!running.get()) return;
        try {
            connection = new WebSocketConnection(uri, this);
            connection.connect();
        } catch (Exception e) {
            log.error("[{}] 连接失败: {}", exchangeName, e.getMessage());
            scheduleReconnect();
        }
    }

    public void disconnect() {
        running.set(false);
        cancelReconnect();
        cancelHeartbeat();
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    public void send(String text) {
        WebSocketConnection conn = connection;
        if (conn != null && conn.isOpen()) conn.send(text);
    }

    public boolean isOpen() {
        WebSocketConnection conn = connection;
        return conn != null && conn.isOpen();
    }

    public String getExchangeName() { return exchangeName; }
    ExchangeWebSocketHandler getHandler() { return handler; }

    void onConnectionOpened(WebSocketConnection conn) {
        connectionOpenTimeMs = System.currentTimeMillis();
        nextReconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;
        int lostTimeout = handler.getConnectionLostTimeoutSeconds();
        if (lostTimeout > 0) {
            conn.setConnectionLostTimeout(lostTimeout);
        }
        handler.onConnected(this);
        startHeartbeat();
        log.debug("[{}] 连接已建立，connectionLostTimeout={}s, heartbeat={}ms", exchangeName,
                lostTimeout, handler.getHeartbeatMessage() != null ? handler.getHeartbeatIntervalMs() : 0);
    }

    void onConnectionClosed(WebSocketConnection conn, int code, String reason, boolean remote) {
        connection = null;
        cancelHeartbeat();
        long durationMs = connectionOpenTimeMs > 0 ? System.currentTimeMillis() - connectionOpenTimeMs : 0;
        connectionOpenTimeMs = 0;
        handler.onClosed(code, reason, remote);
        String codeHint = closeCodeHint(code);
        log.warn("[{}] 连接关闭: code={} ({}) reason=\"{}\" remote={} 存活时长={}ms (将重连)",
                exchangeName, code, codeHint, reason != null ? reason : "", remote, durationMs);
        if (running.get()) scheduleReconnect();
    }

    private static String closeCodeHint(int code) {
        return switch (code) {
            case 1000 -> "正常关闭";
            case 1001 -> "端点离开";
            case 1002 -> "协议错误";
            case 1003 -> "不支持的数据类型";
            case 1006 -> "异常关闭(无关闭帧,可能网络/服务端重置)";
            case 1011 -> "服务器错误";
            case 1015 -> "TLS握手失败";
            default -> "未知";
        };
    }

    void onMessage(String message) {
        lastMessageTimeMs.set(System.currentTimeMillis());
        handler.onMessage(message);
    }

    void onError(Exception ex) {
        log.error("[{}] WebSocket 错误", exchangeName, ex);
        handler.onError(ex);
        if (!isOpen() && running.get()) scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!running.get() || reconnectFuture != null) return;
        log.info("[{}] {}ms 后重连", exchangeName, nextReconnectDelayMs);
        reconnectFuture = scheduler.schedule(() -> {
            reconnectFuture = null;
            if (running.get()) connect();
        }, nextReconnectDelayMs, TimeUnit.MILLISECONDS);
        nextReconnectDelayMs = Math.min((long) (nextReconnectDelayMs * RECONNECT_BACKOFF_MULTIPLIER), MAX_RECONNECT_DELAY_MS);
    }

    private void cancelReconnect() {
        if (reconnectFuture != null) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
    }

    private void startHeartbeat() {
        String msg = handler.getHeartbeatMessage();
        if (msg == null) return;
        long interval = handler.getHeartbeatIntervalMs();
        cancelHeartbeat();
        heartbeatFuture = scheduler.scheduleAtFixedRate(() -> {
            if (running.get() && isOpen()) {
                send(msg);
                log.trace("[{}] 已发送心跳", exchangeName);
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
        log.debug("[{}] 心跳已启动 interval={}ms", exchangeName, interval);
    }

    private void cancelHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
    }

    static class WebSocketConnection extends org.java_websocket.client.WebSocketClient {
        private final ManagedWebSocket manager;

        WebSocketConnection(URI uri, ManagedWebSocket manager) {
            super(uri);
            this.manager = manager;
        }

        @Override
        public void onOpen(org.java_websocket.handshake.ServerHandshake handshake) { manager.onConnectionOpened(this); }
        @Override
        public void onMessage(String message) { manager.onMessage(message); }
        @Override
        public void onMessage(java.nio.ByteBuffer bytes) {
            try {
                byte[] arr = new byte[bytes.remaining()];
                bytes.get(arr);
                manager.getHandler().onBinaryMessage(arr);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ManagedWebSocket.class)
                        .warn("[{}] 二进制消息处理异常: {}", manager.getExchangeName(), e.getMessage());
            }
        }
        @Override
        public void onClose(int code, String reason, boolean remote) { manager.onConnectionClosed(this, code, reason, remote); }
        @Override
        public void onError(Exception ex) { manager.onError(ex); }
    }
}
