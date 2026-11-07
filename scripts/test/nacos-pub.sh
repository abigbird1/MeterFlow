#!/bin/bash
# Nacos 配置发布工具（T47 动态阈值验证用）：nacos-pub.sh <ratio> <floor-cents>
ratio="$1"; floor="$2"
python - "$ratio" "$floor" <<'PYEOF'
import sys, urllib.request, urllib.parse
ratio, floor = sys.argv[1], sys.argv[2]
nl = chr(10)
content = "meterflow:" + nl + "  anomaly:" + nl + "    ratio: " + ratio + nl + "    floor-cents: " + floor + nl + "  package:" + nl + "    low-remaining-percent: 10"
data = urllib.parse.urlencode({"dataId": "meterflow-billing.yml", "group": "DEFAULT_GROUP", "content": content, "type": "yaml"}).encode()
print(urllib.request.urlopen(urllib.request.Request("http://localhost:8848/nacos/v1/cs/configs", data=data, method="POST"), timeout=5).read().decode())
PYEOF
