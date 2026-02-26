# spot-spread 价差套利项目

## 说明
- 前端：Vue 3 + Vite
- 后端：Spring Boot 3.x
- 从 Binance、Bitfinex、CoinEx 通过 WebSocket 获取现货订单簿买一/卖一价
- 套利公式：`(A卖一价/B买一价)-1-手续费 >= 0.1%` 时写入 MySQL
- 币种：BTC、ETH、SOL、XRP、HYPE、BNB

## 启动前
1. 创建 MySQL 数据库：`CREATE DATABASE spot_spread;`
2. 修改 `backend/src/main/resources/application.yml` 中的数据库用户名密码

## 启动
```bash
# 后端
cd backend && mvn spring-boot:run

# 前端
cd frontend && npm install && npm run dev
```

## 端口
- 后端：8080
- 前端：5173（开发模式）
