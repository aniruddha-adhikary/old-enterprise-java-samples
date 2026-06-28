# Evaluation Criteria for Rewrite Hypotheses

## Completeness Checklist (scored 0-1 each, total /30)

### Domain Model (6 points)
- [ ] TradeOrder with all 12 fields
- [ ] Client with all 10 fields (including tiers, KYC, killSwitch)
- [ ] SettlementRecord with all 12 fields
- [ ] Notification with all 9 fields
- [ ] State machines: TradeOrder (NEW→FILLED→SETTLED, REJECTED), SettlementRecord (PENDING→GENERATED→UPLOADED→CONFIRMED/FAILED/DISCREPANCY)
- [ ] Relationships: Client 1:N TradeOrder, TradeOrder 1:1 SettlementRecord, TradeOrder 1:N Notification

### Business Rules (8 points)
- [ ] All 17 rules present with correct priorities
- [ ] Correct reject vs flag behavior for each
- [ ] MaxOrderValue with 10% buffer (BUFFER_MULTIPLIER=1.10)
- [ ] Reversed priority comparator bug preserved (descending order)
- [ ] ShortSaleRule added programmatically (not in XML config)
- [ ] Surveillance rules (Layering, Spoofing) flag but never reject
- [ ] PositionLimit threshold: 100,000 shares
- [ ] DailyVolumeLimit: 50,000 shares per order (misleading name)

### Financial Logic (5 points)
- [ ] Commission rates: PLATINUM 0.5%, GOLD 1.0%, SILVER 1.5%, BRONZE 2.0%
- [ ] Pricing spreads per tier: PLATINUM 0.1%, GOLD 0.2%, SILVER 0.3%, BRONZE 0.5%
- [ ] FX rates: EUR 1.10, GBP 1.55, JPY 0.009, CHF 0.72
- [ ] VaR formula: |notional| * vol * 2.33 * sqrt(1/252)
- [ ] Price deviation threshold: 10%

### Integration Points (5 points)
- [ ] All JMS queues (BIGCORP.TRADE.ORDERS, BIGCORP.NOTIFICATIONS, BIGCORP.TRADE.CONFIRMATIONS, BIGCORP.SETTLEMENT.EVENTS)
- [ ] SOAP pricing service with DB fallback
- [ ] SFTP settlement file transfer (XML + fixed-width DAT format)
- [ ] SMTP email notifications
- [ ] Database (HSQLDB dev / Oracle prod)

### Special Client Logic (3 points)
- [ ] All 10 client overrides (C001-C010) documented
- [ ] Loyalty bonus for C001, C002, C003
- [ ] Henderson zero commission, Acme early access

### Anomalies Preserved (3 points)
- [ ] T+3 settlement doesn't skip weekends
- [ ] VALIDATED and PRICED statuses defined but never used
- [ ] `active` XML attribute not applied to Rule objects
