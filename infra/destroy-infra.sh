#!/bin/bash
# Destroy BigCorp infrastructure containers and all data.
# Use stop-infra.sh if you want to preserve data.
#
# Usage: ./infra/destroy-infra.sh

echo "Destroying BigCorp infrastructure..."
docker stop bigcorp-oracle bigcorp-activemq bigcorp-sftp 2>/dev/null || true
docker rm bigcorp-oracle bigcorp-activemq bigcorp-sftp 2>/dev/null || true
echo "All containers removed."
