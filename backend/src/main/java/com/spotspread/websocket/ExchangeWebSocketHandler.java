package com.spotspread.websocket;

public interface ExchangeWebSocketHandler {

    void onConnected(ManagedWebSocket client);
    void onMessage(String message);

    default void onClosed(int code, String reason) {}
    default void onError(Exception ex) {}
    default String getHeartbeatMessage() { return null; }
    default long getHeartbeatIntervalMs() { return 30_000; }
}
