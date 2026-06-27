---
name: testing-bigcorp-e2e
description: Test the BigCorp Trade Order Management System end-to-end. Use when verifying enterprise Java patterns, web UI, or infrastructure integration changes.
---

# Testing the BigCorp Enterprise Java System

## Overview

This is a multi-module 1990s-era Java enterprise application with 6 modules (common-lib, trade-desk, order-engine, pricing-service, notification-gateway, settlement-gateway). Testing covers both embedded mode (HSQLDB + in-memory ActiveMQ) and real infrastructure mode (Oracle XE + standalone ActiveMQ + SFTP).

## Devin Secrets Needed

No secrets required. All infrastructure credentials are hardcoded in config files (era-appropriate for 90s enterprise Java):
- Oracle: `bigcorp_app` / `bigcorp_2002` on `localhost:1521/XEPDB1`
- SFTP: `bigcorp_settle` / `settle_pass` on `localhost:2222`
- ActiveMQ: default (no auth) on `localhost:61616`

## Prerequisites

### Infrastructure (for real mode testing)

Three Docker containers must be running:
```bash
docker ps --format "table {{.Names}}\t{{.Status}}" | grep bigcorp
```
Expected: `bigcorp-oracle`, `bigcorp-activemq`, `bigcorp-sftp` all "Up".

If not running, start them:
```bash
cd /home/ubuntu/repos/old-enterprise-java-samples
bash infra/start-infra.sh
# Oracle takes ~30-60 seconds to become ready
bash infra/load-schema.sh
```

### Web UI Testing (for Front Controller / .do URL tests)

Tomcat 9 is needed to deploy the trade-desk WAR. Setup steps:
```bash
# Install Tomcat if not present
sudo apt-get install -y tomcat9

# Set JAVA_HOME (Tomcat needs this)
echo 'JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' | sudo tee -a /etc/default/tomcat9

# Build the WAR
cd /home/ubuntu/repos/old-enterprise-java-samples
ant package

# Deploy WAR and add Oracle JDBC + JSch to Tomcat's shared lib
sudo cp dist/trade-desk.war /var/lib/tomcat9/webapps/
sudo cp lib/ojdbc11-21.9.0.0.jar /var/lib/tomcat9/lib/
sudo cp lib/jsch-0.1.55.jar /var/lib/tomcat9/lib/
# DO NOT copy servlet-api-2.5.jar to Tomcat's lib - it conflicts with Tomcat 9's built-in servlet API

# Start Tomcat
sudo systemctl restart tomcat9

# Wait for WAR to deploy, then copy real Oracle config
sleep 5
sudo cp config/real/db.properties /var/lib/tomcat9/webapps/trade-desk/WEB-INF/classes/
sudo cp config/real/settlement.properties /var/lib/tomcat9/webapps/trade-desk/WEB-INF/classes/
```

**Known pitfall:** If you copy `servlet-api-2.5.jar` from `lib/` to Tomcat's shared lib directory, it will cause `NoSuchMethodError: javax.servlet.ServletContext.getClassLoader()` and all webapps will fail to deploy. The servlet API JAR in `lib/` is only for compilation - Tomcat provides its own at runtime.

## Test Commands

### Shell-based E2E Tests

```bash
# Embedded mode (HSQLDB + in-memory ActiveMQ, no Docker needed)
ant run-test

# Real infrastructure mode (Oracle + ActiveMQ + SFTP via Docker)
ant run-test-real

# Clean build verification
ant clean compile
```

### Key Assertions to Verify

- **Total test count:** Should be 44 (34 original + 10 pattern tests T9.1-T9.10)
- **T9.5 (DAO Factory):** Should say "HSQLDB" in embedded mode, "Oracle" in real mode (proves auto-detection, not hardcoded)
- **T9.3 (Connection Pool):** Should show `avail_before=5` (proves pool initialized with correct size)
- **T9.9/T9.10 (Reconciliation):** In real mode, should show "Uploaded test recon file to SFTP" and "Downloaded reconciliation file" and "Updated record -> RECONCILED" (proves real SFTP round-trip)

### Web UI URLs (when Tomcat is running)

| URL | What it shows |
|-----|---------------|
| `http://localhost:8080/trade-desk/` | Index page (JSP) with Main Menu |
| `http://localhost:8080/trade-desk/dashboard.do` | System Dashboard with live DB stats |
| `http://localhost:8080/trade-desk/submitOrder.do` | Order entry form (GET=form, POST=submit) |
| `http://localhost:8080/trade-desk/viewOrders.do` | Recent orders table |
| `http://localhost:8080/trade-desk/viewStatus.do?orderId=X` | Single order detail |
| `http://localhost:8080/trade-desk/order/entry` | Legacy order entry servlet (still works) |
| `http://localhost:8080/trade-desk/order/status` | Legacy order status servlet (still works) |

## Testing Strategy

1. **Build verification first** (`ant clean compile`) - catches compilation issues fast
2. **Embedded mode** (`ant run-test`) - no Docker dependency, verifies all logic works
3. **Real infra mode** (`ant run-test-real`) - verifies Oracle SQL compatibility, real MQ, real SFTP
4. **Web UI** (Tomcat + browser) - verifies Front Controller, Command pattern, Business Delegate through actual HTTP requests

## Common Issues

- **Oracle container not ready:** The Oracle XE container can take 30-60 seconds to become "DATABASE IS READY TO USE". Check with `docker logs bigcorp-oracle 2>&1 | grep -i "ready"`.
- **Test data contamination:** If `ant run-test-real` fails with unique constraint violations, the test cleanup phase may have missed records from a previous run. The test harness cleans up `ORD-TEST-%` records at the start of each run, but if the schema was manually modified this might not work.
- **Tomcat deployment issues:** If WAR doesn't auto-deploy, check `sudo journalctl -u tomcat9 --since "5 min ago"` for errors. The most common issue is the servlet-api conflict mentioned above.
- **ActiveMQ port conflict:** If another process is using port 61616, the standalone ActiveMQ container won't start. Check with `ss -tlnp | grep 61616`.
