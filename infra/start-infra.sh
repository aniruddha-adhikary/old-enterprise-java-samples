#!/bin/bash
# BigCorp Infrastructure Startup Script
# Starts Oracle XE, ActiveMQ, and SFTP server in Docker containers.
#
# Prerequisites: Docker installed and running
# Usage: ./infra/start-infra.sh
#
# After startup, load the Oracle schema:
#   ./infra/load-schema.sh

set -e

echo "=========================================="
echo "  BigCorp Infrastructure Startup"
echo "=========================================="
echo ""

# Oracle XE 21c (standing in for Oracle 8i)
echo "[1/3] Starting Oracle XE database..."
if docker ps -a --format '{{.Names}}' | grep -q '^bigcorp-oracle$'; then
    echo "  Container exists, starting..."
    docker start bigcorp-oracle 2>/dev/null || true
else
    docker run -d --name bigcorp-oracle \
        -p 1521:1521 \
        -e ORACLE_PASSWORD=bigcorp_2002 \
        -e APP_USER=bigcorp_app \
        -e APP_USER_PASSWORD=bigcorp_2002 \
        gvenzl/oracle-xe:21-slim
fi

# ActiveMQ 5.15.x (standing in for IBM MQSeries)
echo "[2/3] Starting ActiveMQ broker..."
if docker ps -a --format '{{.Names}}' | grep -q '^bigcorp-activemq$'; then
    echo "  Container exists, starting..."
    docker start bigcorp-activemq 2>/dev/null || true
else
    docker run -d --name bigcorp-activemq \
        -p 61616:61616 \
        -p 8161:8161 \
        rmohr/activemq:5.15.9-alpine
fi

# SFTP server (standing in for clearinghouse SFTP)
# Uses custom sshd_config to enable legacy key exchange for JSch 0.1.55
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo "[3/3] Starting SFTP server..."
if docker ps -a --format '{{.Names}}' | grep -q '^bigcorp-sftp$'; then
    echo "  Container exists, starting..."
    docker start bigcorp-sftp 2>/dev/null || true
else
    docker run -d --name bigcorp-sftp \
        -p 2222:22 \
        -v "${SCRIPT_DIR}/sshd_config:/etc/ssh/sshd_config:ro" \
        -e SFTP_USERS=bigcorp_settle:settle_pass:::incoming,outgoing \
        atmoz/sftp:alpine
fi

echo ""
echo "Waiting for Oracle XE to be ready (this can take 1-2 minutes)..."
for i in $(seq 1 30); do
    if docker exec bigcorp-oracle bash -c 'echo "SELECT 1 FROM DUAL;" | sqlplus -s bigcorp_app/bigcorp_2002@//localhost:1521/XEPDB1 2>/dev/null' | grep -q "1"; then
        echo "  Oracle ready!"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "  WARNING: Oracle may not be ready yet. Check: docker logs bigcorp-oracle"
    fi
    sleep 5
done

echo ""
echo "=========================================="
echo "  Infrastructure Status"
echo "=========================================="
docker ps --filter name=bigcorp --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo ""
echo "Services:"
echo "  Oracle:   jdbc:oracle:thin:@localhost:1521/XEPDB1 (bigcorp_app/bigcorp_2002)"
echo "  ActiveMQ: tcp://localhost:61616 (admin console: http://localhost:8161)"
echo "  SFTP:     localhost:2222 (bigcorp_settle/settle_pass)"
echo ""
echo "Next: Run ./infra/load-schema.sh to create tables and load sample data."
