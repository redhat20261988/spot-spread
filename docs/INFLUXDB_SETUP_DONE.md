# InfluxDB + Grafana 已安装并初始化

## 当前状态

- **Docker**：已安装并启动
- **InfluxDB**：已启动，http://localhost:8086
  - 用户：admin
  - 密码：admin123456
  - 组织：spot-spread
  - 存储桶：spot-spread
  - 保留策略：7 天
  - API Token：已写入 `application.yml` 和 Grafana 数据源
- **Grafana**：已启动，http://localhost:3000
  - 默认账号：admin/admin
  - InfluxDB 数据源已预置（需重启 Grafana 后生效）

## 启动/停止

```bash
cd spot-spread
docker compose up -d    # 启动
docker compose down    # 停止
docker compose restart grafana  # 重启 Grafana
```

## 后端应用

后端 `application.yml` 已包含 InfluxDB Token 默认值，可直接启动：

```bash
cd backend && mvn spring-boot:run
```

或通过环境变量覆盖：`export INFLUXDB_TOKEN=your-token`
