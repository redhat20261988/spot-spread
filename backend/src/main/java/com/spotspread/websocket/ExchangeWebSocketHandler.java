package com.spotspread.websocket;

public interface ExchangeWebSocketHandler {

    void onConnected(ManagedWebSocket client);
    void onMessage(String message);

    /** 收到二进制帧时调用，默认转 UTF-8 后交给 onMessage；若需解压等处理可覆盖 */
    default void onBinaryMessage(byte[] data) {
        if (data != null && data.length > 0) {
            onMessage(new String(data, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    default void onClosed(int code, String reason, boolean remote) {}
    default void onError(Exception ex) {}
    /** 应用层心跳消息（如 JSON ping），null 表示不使用 */
    default String getHeartbeatMessage() { return null; }
    default long getHeartbeatIntervalMs() { return 30_000; }
    /** WebSocket 协议层 connection lost 超时（秒），>0 时库会自动发 ping 保活，0 表示不使用 */
    default int getConnectionLostTimeoutSeconds() { return 0; }
}
