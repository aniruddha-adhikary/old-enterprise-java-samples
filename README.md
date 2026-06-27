# BigCorp Trade Order Management System

A multi-application Java project emulating late 1990s / early 2000s enterprise Java technology. Seven interconnected applications communicate via JMS message queues, SOAP web services, SFTP batch file transfer, and a shared database.

## Architecture

```
[Trader Browser]
       |  HTTP POST
       v
  [trade-desk]  ──XML/JMS──>  [order-engine]  ──SOAP/HTTP──>  [pricing-service]
       |                            |
       | JDBC                       | JDBC + Rule Engine
       v                            v
   [HSQLDB]  <───────────────  [HSQLDB]
                                    |
                              XML/JMS (notifications queue)
                                    |
                                    v
                          [notification-gateway]
                            /              \
                     SMTP Email         HTTP POST (SMS API)

                          [settlement-gateway]
                            /              \
                    SFTP Upload         SFTP Poll
                   (to clearinghouse)  (from clearinghouse)
                          |
                        JDBC
                          |
                      [HSQLDB]

                          [audit-service]
                               |
                   XML/JMS (confirmations queue)
                               |
                             JDBC
                          /        \
                    AUDIT_LOG   BILLING_LEDGER

  [order-engine]  ──CCI/TCP──>  [connector]  ──TCP──>  [Mainframe EIS (CICS)]
                                     |                    (unreachable in demo)
                                     | JDBC fallback
                                     v
                                  [HSQLDB]
                                (CLIENTS table)
```

## Modules

| Module | Type | Technology | Description |
|--------|------|------------|-------------|
| **common-lib** | Shared JAR | JDBC, JMS, DOM/SAX XML | Domain models, XML marshalling, DB helpers, Rule Engine, MQ helpers |
| **trade-desk** | WAR (Servlet/JSP) | Servlet 2.3, JSP, JMS | Order entry web UI and status pages |
| **order-engine** | Standalone JMS consumer | JMS, SOAP client, JDBC | Processes orders from MQ, runs rules, calls pricing, persists |
| **pricing-service** | WAR (SOAP) | WSDL, hand-rolled SOAP, JDBC | Real-time pricing quotes via SOAP endpoint |
| **notification-gateway** | Standalone JMS consumer | JMS, JavaMail, HTTP | Email and SMS notification dispatch |
| **settlement-gateway** | Batch processor | SFTP (JSch), JDBC, XML | End-of-day settlement file generation and SFTP transfer |
| **audit-service** | Standalone JMS consumer | JMS, JDBC | Audit logging and billing ledger for order lifecycle events |
| **connector** | Resource Adapter (.rar) | JCA / CCI (hand-rolled), JDBC fallback | Mainframe back-office account/credit verification via JCA resource adapter |

## Tech Stack

- **Java 8** (code written in pre-generics 1.4 style)
- **Apache Ant** build system
- **Servlet 2.3 / JSP** (Tomcat era)
- **JMS / ActiveMQ** (embedded, standing in for IBM MQSeries)
- **SOAP / WSDL** (hand-rolled endpoint, Apache Axis client stubs)
- **JDBC** (raw DriverManager, no connection pool)
- **HSQLDB** (embedded, standing in for Oracle 8i)
- **JavaMail** (SMTP email dispatch)
- **JSch** (SFTP file transfer)
- **XML** (DOM, SAX, and string concatenation approaches)
- **Custom Rule Engine** (XML-configured business rules)
- **JCA / J2EE Connector Architecture** — vendor resource adapter stand-in

## Prerequisites

- JDK 8+ (tested with OpenJDK 17)
- Apache Ant 1.10+

## Quick Start

```bash
# Download dependencies
ant deps

# Compile all modules
ant compile

# Run the end-to-end demo
ant run-demo
```

The demo runs the full pipeline with embedded infrastructure (HSQLDB in-memory DB, ActiveMQ embedded broker, local filesystem SFTP fallback):

1. Initializes database and message queues
2. Starts order engine, notification gateway, and audit service listeners
3. Submits a sample trade order (500 shares MSFT @ $25.75)
4. Order engine processes it through the rule engine and pricing service
5. Email notification dispatched (logged in dev mode)
6. Audit service records events to AUDIT_LOG and charges commission to BILLING_LEDGER
7. Settlement batch generates XML and fixed-width files

## Build Targets

| Target | Description |
|--------|-------------|
| `ant deps` | Download dependency JARs from Maven Central |
| `ant compile` | Compile all modules |
| `ant package` | Create JAR/WAR artifacts |
| `ant run-demo` | Run the end-to-end demo |
| `ant clean` | Remove build artifacts |
| `ant clean-all` | Remove build artifacts and downloaded libs |

## Business Rules (Rule Engine)

The custom rule engine evaluates orders through a chain of rules:

| Rule | Priority | Description |
|------|----------|-------------|
| MaxOrderValue | 100 | Checks order value against client limit (with 10% buffer for Henderson Capital) |
| ClientTier | 90 | Validates client is active, sets processing priority based on tier |
| MarketHours | 80 | Checks market hours (queues orders placed outside hours instead of rejecting) |
| SpecialClients | 50 | Applies hardcoded overrides for specific client accounts |

## Commission Rates (by Client Tier)

Commission is calculated by `CommissionCalculator` based on the client's tier (resolves JIRA-2501):

| Tier | Rate | Example ($100k order) |
|------|------|-----------------------|
| PLATINUM | 0.5% | $500 |
| GOLD | 1.0% | $1,000 |
| SILVER | 1.5% | $1,500 |
| BRONZE | 2.0% | $2,000 |

## License

MIT
