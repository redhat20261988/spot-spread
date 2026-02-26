# InfluxDB + Grafana 监控配置

## 一、安装

### 方式一：Docker Compose（推荐）

```bash
cd spot-spread
./scripts/setup-influxdb-grafana.sh
```

或手动执行：

```bash
docker compose up -d
```

### 方式二：手动 Docker

```bash
docker run -d --name influxdb -p 8086:8086 -v influxdb-data:/var/lib/influxdb2 influxdb:2.7
docker run -d --name grafana -p 3000:3000 -v $(pwd)/grafana/provisioning:/etc/grafana/provisioning grafana/grafana:latest
```

## 二、InfluxDB 初始化

1. 访问 http://localhost:8086
2. 创建用户（Username / Password）
3. 创建组织（Organization）: `spot-spread`
4. 创建存储桶（Bucket）: `spot-spread`，**Retention 选择 7 days**
5. 完成初始化后，进入 "API Tokens" 创建 Token
6. 复制 Token，用于：
   - 后端 `application.yml` 或环境变量 `INFLUXDB_TOKEN`
   - `grafana/provisioning/datasources/influxdb.yml` 中 `secureJsonData.token`（替换 `your-influxdb-token-here`）

## 三、Grafana 数据源

若使用 provisioning，编辑 `grafana/provisioning/datasources/influxdb.yml` 中的 `secureJsonData.token`，然后重启 Grafana。

或手动添加：
1. Configuration → Data Sources → Add data source
2. 选择 InfluxDB
3. Query Language: Flux
4. URL: `http://influxdb:8086`（Docker Compose 同网络）或 `http://localhost:8086`（宿主机）
5. Organization、Default Bucket 填 `spot-spread`
6. Auth: Token 填 InfluxDB API Token

## 四、仪表板

Spot Spread 监控仪表板位于 **Spot Spread** 文件夹，包含：

1. **交易所价格延迟 (ms)**：按 exchange、symbol 展示 Binance/OKX/Bybit 的价格延迟
2. **交易所组合价差**：按 ex_sell、ex_buy、symbol 展示 spot_spread
3. **交易所组合利润率 (%)**：展示 profit_margin_pct

可在 Grafana 中通过变量筛选 exchange、symbol 等。
