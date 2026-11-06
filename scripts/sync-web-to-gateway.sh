#!/bin/bash
# 前端构建并同步到网关静态资源（一体化交付，meterflow.md #7）
set -e
cd "$(dirname "$0")/../web"
npm run build
rm -rf ../server/meterflow-gateway/src/main/resources/static
mkdir -p ../server/meterflow-gateway/src/main/resources/static
cp -r dist/* ../server/meterflow-gateway/src/main/resources/static/
echo "已同步 web/dist → gateway/static；重新打包启动网关即可单端口访问"
