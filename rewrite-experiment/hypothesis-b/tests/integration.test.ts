import { MessageBroker, QUEUES } from '../src/integration/MessageBroker';
import { NotificationService } from '../src/notifications/NotificationService';
import { AuditService } from '../src/audit/AuditService';
import { RegulatoryReportGenerator } from '../src/regulatory/RegulatoryReportGenerator';
import { assessRisk } from '../src/risk/ExposureCalculator';
import { RiskStatus, VAR_FLAG_THRESHOLD } from '../src/domain/RiskOrder';
import { TradeOrder, OrderStatus } from '../src/domain/TradeOrder';

describe('Message Broker', () => {
  test('all legacy queues mapped', () => {
    expect(QUEUES.TRADE_ORDERS).toBe('bigcorp.trade.orders');
    expect(QUEUES.TRADE_CONFIRMATIONS).toBe('bigcorp.trade.confirmations');
    expect(QUEUES.NOTIFICATIONS).toBe('bigcorp.notifications');
    expect(QUEUES.SETTLEMENT_EVENTS).toBe('bigcorp.settlement.events'); // BUG-009
    expect(QUEUES.DERIVATIVES_ORDERS).toBe('bigcorp.derivatives.orders');
    expect(QUEUES.DERIVATIVES_CONFIRMS).toBe('bigcorp.derivatives.confirms');
    expect(QUEUES.DERIVATIVES_PRICING).toBe('bigcorp.derivatives.pricing');
    expect(QUEUES.RISK_INBOUND).toBe('risk.orders.inbound');
    expect(QUEUES.RISK_OUTBOUND).toBe('risk.results.outbound');
  });

  test('pub/sub in-memory dispatch', async () => {
    const broker = new MessageBroker();
    await broker.connect();
    const received: unknown[] = [];
    broker.subscribe('test.queue', async (msg) => { received.push(msg); });
    await broker.publish('test.queue', { orderId: 'ORD-1' });
    expect(received.length).toBe(1);
  });
});

describe('Notification Service', () => {
  test('creates order confirmation', () => {
    const svc = new NotificationService();
    const order = {
      orderId: 'ORD-123', clientId: 'C001', symbol: 'MSFT', quantity: 500,
      side: 'BUY' as const, price: 25.50, requestedPrice: 25.75,
      status: OrderStatus.FILLED, orderDate: new Date(), lastModified: new Date(),
      notes: null, surveillanceFlags: '',
    };
    const client = {
      clientId: 'C001', name: 'Acme', email: 'test@acme.com', phone: null,
      tier: 'GOLD' as const, maxOrderValue: 500000, active: true, kycStatus: 'APPROVED', killSwitch: 'N',
    };

    const notif = svc.createOrderConfirmation(order, client);
    expect(notif.type).toBe('ORDER_CONFIRM');
    expect(notif.recipient).toBe('test@acme.com');
    expect(notif.status).toBe('PENDING');
    expect(notif.notificationId).toMatch(/^N-ORD-123-/);
  });

  test('creates order rejection', () => {
    const svc = new NotificationService();
    const order = {
      orderId: 'ORD-456', clientId: 'C002', symbol: 'ENRN', quantity: 100,
      side: 'BUY' as const, price: 0, requestedPrice: 50,
      status: OrderStatus.REJECTED, orderDate: new Date(), lastModified: new Date(),
      notes: null, surveillanceFlags: '',
    };
    const client = {
      clientId: 'C002', name: 'Henderson', email: 'orders@henderson.com', phone: null,
      tier: 'PLATINUM' as const, maxOrderValue: 5000000, active: true, kycStatus: 'APPROVED', killSwitch: 'N',
    };

    const notif = svc.createOrderRejection(order, client, 'Restricted symbol');
    expect(notif.type).toBe('ORDER_REJECT');
    expect(notif.notificationId).toMatch(/^N-ORD-456-REJ-/);
  });

  test('dispatch in dev mode logs to console', async () => {
    const svc = new NotificationService();
    const notif = {
      notificationId: 'N-1', type: 'ORDER_CONFIRM', recipient: 'test@test.com',
      subject: 'Test', body: 'Test body', channel: 'EMAIL' as const,
      status: 'PENDING' as const, orderId: 'ORD-1', createdDate: new Date(),
      sentDate: null, retryCount: 0,
    };
    const result = await svc.dispatch(notif);
    expect(result).toBe(true);
    expect(notif.status).toBe('SENT');
  });
});

describe('Audit Service', () => {
  test('logs order filled', () => {
    const svc = new AuditService();
    svc.logOrderFilled({
      orderId: 'ORD-1', clientId: 'C001', symbol: 'MSFT', quantity: 500,
      side: 'BUY', price: 25.50, requestedPrice: 25.75,
      status: OrderStatus.FILLED, orderDate: new Date(), lastModified: new Date(),
      notes: null, surveillanceFlags: '',
    });
    const log = svc.getAuditLog();
    expect(log.length).toBe(1);
    expect(log[0].eventType).toBe('ORDER_FILLED');
    expect(log[0].sourceSystem).toBe('order-engine');
  });

  test('charges billing with correct tier rate', () => {
    const svc = new AuditService();
    const entry = svc.chargeBilling({
      orderId: 'ORD-2', clientId: 'C001', symbol: 'MSFT', quantity: 1000,
      side: 'BUY', price: 100, requestedPrice: 100,
      status: OrderStatus.FILLED, orderDate: new Date(), lastModified: new Date(),
      notes: null, surveillanceFlags: '',
    }, 'GOLD');

    // GOLD rate = 1%, gross = 100,000, commission = 1,000
    expect(entry.grossAmount).toBe(100000);
    expect(entry.commissionAmount).toBe(1000);
    expect(entry.netAmount).toBe(101000);
  });
});

describe('Risk Assessment', () => {
  test('assesses order and computes VaR', () => {
    const result = assessRisk({
      sourceOrderId: 'ORD-1',
      clientId: 'C001',
      symbol: 'MSFT',
      quantity: 1000,
      side: 'BUY',
      price: 25.50,
    });

    expect(result.notionalValue).toBe(25500);
    expect(result.exposureContribution).toBe(25500); // BUY = positive
    expect(result.varContribution).toBeGreaterThan(0);
    expect(result.riskStatus).toBe(RiskStatus.ASSESSED);
  });

  test('SELL gives negative exposure', () => {
    const result = assessRisk({
      sourceOrderId: 'ORD-2',
      clientId: 'C001',
      symbol: 'IBM',
      quantity: 500,
      side: 'SELL',
      price: 120,
    });
    expect(result.exposureContribution).toBe(-60000);
  });

  test('large notional flags for review', () => {
    const result = assessRisk({
      sourceOrderId: 'ORD-3',
      clientId: 'C002',
      symbol: 'MSFT',
      quantity: 100000,
      side: 'BUY',
      price: 25.50,
    });
    expect(result.varContribution).toBeGreaterThan(VAR_FLAG_THRESHOLD);
    expect(result.riskStatus).toBe(RiskStatus.FLAGGED);
  });
});

describe('Regulatory Reporting', () => {
  const generator = new RegulatoryReportGenerator();
  const orders: TradeOrder[] = [
    {
      orderId: 'ORD-1', clientId: 'C001', symbol: 'MSFT', quantity: 500,
      side: 'BUY' as const, price: 25.50, requestedPrice: 25.75,
      status: OrderStatus.FILLED, orderDate: new Date(2024, 0, 15),
      lastModified: new Date(), notes: null, surveillanceFlags: '',
    },
    {
      orderId: 'ORD-2', clientId: 'C002', symbol: 'IBM', quantity: 100,
      side: 'SELL' as const, price: 120.25, requestedPrice: 120,
      status: OrderStatus.SETTLED, orderDate: new Date(2024, 0, 16),
      lastModified: new Date(), notes: null, surveillanceFlags: '',
    },
    {
      orderId: 'ORD-3', clientId: 'C003', symbol: 'ORCL', quantity: 200,
      side: 'BUY' as const, price: 15.38, requestedPrice: 15.50,
      status: OrderStatus.NEW, orderDate: new Date(2024, 0, 17),
      lastModified: new Date(), notes: null, surveillanceFlags: '',
    },
  ];

  test('fixed-width report includes only FILLED/SETTLED orders', () => {
    const report = generator.generateFixedWidth(orders);
    expect(report).toContain('HDR');
    expect(report).toContain('BIGCORP');
    expect(report).toContain('ORD-1');
    expect(report).toContain('ORD-2');
    expect(report).not.toContain('ORD-3'); // NEW status excluded
    expect(report).toContain('TRL');
    // Trailer should show count 2
    const lines = report.split('\n');
    const trailer = lines[lines.length - 1];
    expect(trailer).toMatch(/TRL\s+2/);
  });

  test('XML report structure', () => {
    const report = generator.generateXml(orders);
    expect(report).toContain('<?xml');
    expect(report).toContain('<RegulatoryReport');
    expect(report).toContain('firm="BIGCORP"');
    expect(report).toContain('<Trade>');
    expect(report).toContain('ORD-1');
    expect(report).not.toContain('ORD-3');
  });

  test('report log populated', () => {
    const gen = new RegulatoryReportGenerator();
    gen.generateFixedWidth(orders);
    gen.generateXml(orders);
    const log = gen.getReportLog();
    expect(log.length).toBe(2);
    expect(log[0].reportType).toBe('DAILY_TRADE_REPORT');
  });
});
