#!/bin/bash
# InfluxDB + Grafana 安装脚本（Docker Compose 推荐）
# 在安装 Docker 后执行此脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== 检查 Docker ==="
if ! command -v docker &> /dev/null; then
    echo "Docker 未安装。请先安装 Docker："
    echo "  Ubuntu/Debian: sudo apt update && sudo apt install -y docker.io docker-compose"
    echo "  CentOS/RHEL: sudo yum install -y docker && sudo systemctl start docker"
    exit 1
fi

cd "$PROJECT_ROOT"

echo "=== 启动 InfluxDB + Grafana (docker-compose) ==="
docker compose up -d

echo ""
echo "安装完成！"
echo "1. InfluxDB: http://localhost:8086"
echo "   - 首次访问完成初始化：创建 admin 用户、组织 spot-spread、存储桶 spot-spread，Retention 设为 7 days"
echo "   - 获取 API Token 后："
echo "     - 编辑 grafana/provisioning/datasources/influxdb.yml，将 your-influxdb-token-here 替换为实际 Token"
echo "     - 编辑 application.yml 或设置环境变量 INFLUXDB_TOKEN"
echo "     - 重启 Grafana: docker compose restart grafana"
echo ""
echo "2. Grafana: http://localhost:3000 - 默认账号 admin/admin"
echo "   - 仪表板 Spot Spread > Spot Spread 监控 已自动加载"
