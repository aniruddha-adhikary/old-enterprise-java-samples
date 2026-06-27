#!/bin/bash
# Stop BigCorp infrastructure containers.
# Data is preserved (containers are stopped, not removed).
#
# Usage: ./infra/stop-infra.sh
# To remove containers and data: ./infra/destroy-infra.sh

echo "Stopping BigCorp infrastructure..."
docker stop bigcorp-oracle bigcorp-activemq bigcorp-sftp 2>/dev/null || true
echo "All containers stopped."
docker ps --filter name=bigcorp --format "table {{.Names}}\t{{.Status}}"
