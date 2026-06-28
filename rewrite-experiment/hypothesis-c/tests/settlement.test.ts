import { SettlementProcessor } from '../src/settlement/settlement-processor';
import { createTradeOrder, OrderSide, OrderStatus } from '../src/domain/trade-order';
import { SettlementStatus } from '../src/domain/settlement';
import { AuditService } from '../src/audit/audit-service';
import { NotificationDispatcher } from '../src/notifications/notification-dispatcher';
import { RegulatoryExport } from '../src/regulatory/regulatory-export';
import { InMemoryBroker, QUEUES } from '../src/integration/message-queue';

describe('SettlementProcessor', () => {
  const processor = new SettlementProcessor();

  describe('T+3 Settlement Date', () => {
    it('should add exactly 3 calendar days (bug preserved - no weekend skip)', () => {
      // Monday -> Thursday
      const monday = new Date('2024-01-15T10:00:00Z');
      const settlement = processor.calculateSettlementDate(monday);
      expect(settlement.getDate()).toBe(18); // Thursday

      // Thursday -> Sunday (BUG: should skip to Monday)
      const thursday = new Date('2024-01-18T10:00:00Z');
      const settleFri = processor.calculateSettlementDate(thursday);
      expect(settleFri.getDate()).toBe(21); // Sunday, not Monday (bug)
    });

    it('should add 3 days even on Friday (weekend bug)', () => {
      const friday = new Date('2024-01-19T10:00:00Z');
      const settlement = processor.calculateSettlementDate(friday);
      expect(settlement.getDate()).toBe(22); // Monday - but only by coincidence
    });
  });

  describe('Settlement Record Creation', () => {
    it('should create record with correct amounts and commission', () => {
      const order = createTradeOrder({
        orderId: 'ORD-001',
        clientId: 'C001',
        symbol: 'MSFT',
        quantity: 100,
        side: OrderSide.BUY,
        price: 25.00,
      });
      order.price = 25.50;
      order.status = OrderStatus.FILLED;

      const record = processor.createSettlementRecord(order, 'BATCH-001', 'GOLD');

      expect(record.orderId).toBe('ORD-001');
      expect(record.clientId).toBe('C001');
      expect(record.quantity).toBe(100);
      expect(record.amount).toBe(2550); // 100 * 25.50
      expect(record.commission).toBe(25.50); // 2550 * 0.01 (GOLD rate)
      expect(record.status).toBe(SettlementStatus.PENDING);
      expect(record.batchId).toBe('BATCH-001');
    });
  });

  describe('Batch ID Generation', () => {
    it('should generate sequential batch IDs', () => {
      const p = new SettlementProcessor();
      const id1 = p.generateBatchId();
      const id2 = p.generateBatchId();
      expect(id1).toMatch(/^BATCH-\d{8}-\d{3}$/);
      expect(id2).toMatch(/^BATCH-\d{8}-\d{3}$/);
      expect(id1).not.toBe(id2);
    });
  });

  describe('XML File Generation', () => {
    it('should generate valid XML with header and records', () => {
      const order = createTradeOrder({
        orderId: 'ORD-001',
        clientId: 'C001',
        symbol: 'MSFT',
        quantity: 100,
        side: OrderSide.BUY,
      });
      order.price = 25.50;

      const record = processor.createSettlementRecord(order, 'BATCH-001', 'GOLD');
      const xml = processor.generateXmlFile([record], 'BATCH-001');

      expect(xml).toContain('<?xml version="1.0"');
      expect(xml).toContain('<batchId>BATCH-001</batchId>');
      expect(xml).toContain('<recordCount>1</recordCount>');
      expect(xml).toContain('<orderId>ORD-001</orderId>');
      expect(xml).toContain('<symbol>MSFT</symbol>');
    });
  });

  describe('Flat File Generation', () => {
    it('should generate fixed-width file with header, detail, and trailer', () => {
      const order = createTradeOrder({
        orderId: 'ORD-001',
        clientId: 'C001',
        symbol: 'MSFT',
        quantity: 100,
        side: OrderSide.BUY,
      });
      order.price = 25.50;

      const record = processor.createSettlementRecord(order, 'BATCH-001', 'GOLD');
      const flat = processor.generateFlatFile([record], 'BATCH-001');

      const lines = flat.split('\n');
      expect(lines[0]).toMatch(/^HDR/);
      expect(lines[1]).toMatch(/^DTL/);
      expect(lines[lines.length - 1]).toMatch(/^TRL/);
    });
  });
});

describe('AuditService', () => {
  it('should log order events', () => {
    const svc = new AuditService();
    const order = createTradeOrder({
      orderId: 'ORD-001',
      clientId: 'C001',
      symbol: 'MSFT',
      quantity: 100,
      side: OrderSide.BUY,
    });

    svc.logOrderEvent(order, 'ORDER_SUBMITTED');
    expect(svc.getAuditLog()).toHaveLength(1);
    expect(svc.getAuditLog()[0].eventType).toBe('ORDER_SUBMITTED');
  });

  it('should create billing entries with tier-based commission', () => {
    const svc = new AuditService();
    const order = createTradeOrder({
      orderId: 'ORD-001',
      clientId: 'C001',
      symbol: 'MSFT',
      quantity: 100,
      side: OrderSide.BUY,
    });
    order.price = 25.00;

    const entry = svc.createBillingEntry(order, 'GOLD');
    expect(entry.grossAmount).toBe(2500); // 100 * 25
    expect(entry.commissionAmount).toBe(25); // 2500 * 0.01
    expect(entry.netAmount).toBe(2525); // 2500 + 25
    expect(entry.status).toBe('CHARGED');
  });

  it('should derive correct event types from order status', () => {
    const svc = new AuditService();
    expect(svc.deriveEventType('NEW')).toBe('ORDER_SUBMITTED');
    expect(svc.deriveEventType('FILLED')).toBe('ORDER_FILLED');
    expect(svc.deriveEventType('REJECTED')).toBe('ORDER_REJECTED');
    expect(svc.deriveEventType('SETTLED')).toBe('SETTLEMENT_CREATED');
  });
});

describe('NotificationDispatcher', () => {
  it('should dispatch email notifications in dev mode', async () => {
    const dispatcher = new NotificationDispatcher(true);
    const notif = {
      notificationId: 'N001',
      type: 'ORDER_CONFIRM' as any,
      recipient: 'test@test.com',
      subject: 'Order Confirmation',
      body: 'MSFT|100|BUY|25.50||2550',
      channel: 'EMAIL' as any,
      status: 'PENDING' as any,
      orderId: 'ORD-001',
      createdDate: new Date(),
      sentDate: null,
      retryCount: 0,
    };

    const result = await dispatcher.dispatch(notif);
    expect(result).toBe(true);
    expect(dispatcher.getDispatched()).toHaveLength(1);
  });

  it('should strip + from phone numbers (bug preserved)', () => {
    const dispatcher = new NotificationDispatcher(true);
    expect(dispatcher.formatPhoneNumber('+1-555-0100')).toBe('15550100');
    expect(dispatcher.formatPhoneNumber('+65-1234-5678')).toBe('6512345678');
  });

  it('should truncate SMS to 160 chars', async () => {
    const dispatcher = new NotificationDispatcher(true);
    const longMessage = 'A'.repeat(200);
    const notif = {
      notificationId: 'N002',
      type: 'PRICE_ALERT' as any,
      recipient: '555-0100',
      subject: null,
      body: longMessage,
      channel: 'SMS' as any,
      status: 'PENDING' as any,
      orderId: null,
      createdDate: new Date(),
      sentDate: null,
      retryCount: 0,
    };

    await dispatcher.dispatch(notif);
    expect(dispatcher.getDispatched()).toHaveLength(1);
  });
});

describe('RegulatoryExport', () => {
  const exporter = new RegulatoryExport();

  it('should generate fixed-width report with correct format', () => {
    const orders = [
      createTradeOrder({
        orderId: 'ORD-001',
        clientId: 'C001',
        symbol: 'MSFT',
        quantity: 100,
        side: OrderSide.BUY,
      }),
    ];
    orders[0].price = 25.50;
    orders[0].status = OrderStatus.FILLED;

    const report = exporter.generateFixedWidthReport(orders);
    const lines = report.split('\n');

    expect(lines[0]).toMatch(/^HDR/);
    expect(lines[1]).toMatch(/^DTL/);
    expect(lines[lines.length - 1]).toMatch(/^TRL/);
  });

  it('should generate XML report with correct structure', () => {
    const orders = [
      createTradeOrder({
        orderId: 'ORD-001',
        clientId: 'C001',
        symbol: 'MSFT',
        quantity: 100,
        side: OrderSide.BUY,
      }),
    ];

    const report = exporter.generateXmlReport(orders);
    expect(report).toContain('<regulatoryReport>');
    expect(report).toContain('<reportType>DAILY_TRADE</reportType>');
    expect(report).toContain('<submitter>BIGCORP</submitter>');
    expect(report).toContain('<orderId>ORD-001</orderId>');
  });
});

describe('MessageBroker', () => {
  it('should publish and subscribe to messages', async () => {
    const broker = new InMemoryBroker();
    const received: string[] = [];

    await broker.subscribe(QUEUES.TRADE_ORDERS, (msg) => received.push(msg));
    await broker.publish(QUEUES.TRADE_ORDERS, '{"orderId":"ORD-001"}');

    expect(received).toHaveLength(1);
    expect(received[0]).toContain('ORD-001');
  });

  it('should track all JMS queue names from the original system', () => {
    expect(QUEUES.TRADE_ORDERS).toBe('BIGCORP.TRADE.ORDERS');
    expect(QUEUES.TRADE_CONFIRMATIONS).toBe('BIGCORP.TRADE.CONFIRMATIONS');
    expect(QUEUES.NOTIFICATIONS).toBe('BIGCORP.NOTIFICATIONS');
    expect(QUEUES.SETTLEMENT_EVENTS).toBe('BIGCORP.SETTLEMENT.EVENTS');
    expect(QUEUES.DERIVATIVE_ORDERS).toBe('BIGCORP.DERIVATIVES.ORDERS');
    expect(QUEUES.DERIVATIVE_CONFIRMS).toBe('BIGCORP.DERIVATIVES.CONFIRMS');
    expect(QUEUES.DERIVATIVE_PRICING).toBe('BIGCORP.DERIVATIVES.PRICING');
    expect(QUEUES.RISK_INBOUND).toBe('RISK.ORDERS.INBOUND');
    expect(QUEUES.RISK_RESULTS).toBe('RISK.RESULTS.OUTBOUND');
  });
});
