import { SettlementService } from '../src/settlement/SettlementService';
import { ReconciliationProcessor } from '../src/settlement/ReconciliationProcessor';
import { calculateSettlementDate, generateBatchId } from '../src/domain/SettlementRecord';
import { TradeOrder, OrderStatus } from '../src/domain/TradeOrder';
import { Client } from '../src/domain/Client';

describe('Settlement Service', () => {
  const svc = new SettlementService();
  const clients = new Map<string, Client>();
  clients.set('C001', {
    clientId: 'C001', name: 'Test', email: 'test@test.com', phone: null,
    tier: 'GOLD', maxOrderValue: 500000, active: true, kycStatus: 'APPROVED', killSwitch: 'N',
  });

  const filledOrder: TradeOrder = {
    orderId: 'ORD-1234567890',
    clientId: 'C001',
    symbol: 'MSFT',
    quantity: 500,
    side: 'BUY',
    price: 25.50,
    requestedPrice: 25.75,
    status: OrderStatus.FILLED,
    orderDate: new Date(2024, 0, 15, 10, 0, 0),
    lastModified: new Date(),
    notes: null,
    surveillanceFlags: '',
  };

  test('generates batch with correct amount and commission', () => {
    const result = svc.processBatch([filledOrder], clients);
    expect(result.records.length).toBe(1);
    const rec = result.records[0];
    expect(rec.amount).toBe(500 * 25.50); // quantity * price
    expect(rec.commission).toBeCloseTo(500 * 25.50 * 0.01, 4); // GOLD rate
    expect(rec.status).toBe('GENERATED');
  });

  test('generates XML content', () => {
    const result = svc.processBatch([filledOrder], clients);
    expect(result.xmlContent).toContain('<?xml');
    expect(result.xmlContent).toContain('SettlementBatch');
    expect(result.xmlContent).toContain('ORD-1234567890');
  });

  test('generates DAT content with header/trailer', () => {
    const result = svc.processBatch([filledOrder], clients);
    const lines = result.datContent.split('\n');
    expect(lines[0]).toMatch(/^HDR/);
    expect(lines[lines.length - 1]).toMatch(/^TRL/);
  });

  test('empty batch produces no records', () => {
    const result = svc.processBatch([], clients);
    expect(result.records.length).toBe(0);
  });

  test('skips non-FILLED orders', () => {
    const newOrder = { ...filledOrder, status: OrderStatus.NEW };
    const result = svc.processBatch([newOrder], clients);
    expect(result.records.length).toBe(0);
  });
});

describe('Settlement Date Calculation', () => {
  test('BUG-004: T+3 calendar days, does NOT skip weekends', () => {
    // Friday -> Monday (would be T+3 business days = Wednesday)
    const friday = new Date(2024, 0, 12); // Jan 12, 2024 = Friday
    const settlement = calculateSettlementDate(friday);
    // T+3 calendar = Monday Jan 15
    expect(settlement.getDate()).toBe(15);
    expect(settlement.getDay()).toBe(1); // Monday
  });

  test('Monday -> Thursday', () => {
    const monday = new Date(2024, 0, 15);
    const settlement = calculateSettlementDate(monday);
    expect(settlement.getDate()).toBe(18); // Thursday
  });
});

describe('Batch ID Format', () => {
  test('BATCH-yyyyMMdd-NNN format', () => {
    const date = new Date(2024, 5, 15); // June 15, 2024
    expect(generateBatchId(date, 1)).toBe('BATCH-20240615-001');
    expect(generateBatchId(date, 42)).toBe('BATCH-20240615-042');
  });
});

describe('Reconciliation Processor', () => {
  const processor = new ReconciliationProcessor();

  test('parses DAT file format', () => {
    const datContent = [
      'HDR' + ' '.repeat(77),
      'SR-123-456          EXT-001   CONF      0000125000202401150000000000000000000000',
      'SR-789-012          EXT-002   REJC      0000050000202401150000000000000000000000',
      'TRL         2',
    ].join('\n');

    const entries = processor.parseDatFile(datContent);
    expect(entries.length).toBe(2);
    expect(entries[0].recordId).toBe('SR-123-456');
    expect(entries[0].externalRef).toBe('EXT-001');
    expect(entries[0].status).toBe('CONF');
    expect(entries[1].status).toBe('REJC');
  });

  test('reconciles CONF as RECONCILED', () => {
    const records = new Map();
    records.set('REC-001', {
      recordId: 'REC-001', orderId: 'ORD-1', clientId: 'C001',
      symbol: 'MSFT', quantity: 100, side: 'BUY', amount: 2500,
      commission: 25, tradeDate: new Date(), settlementDate: new Date(),
      status: 'UPLOADED', batchId: 'B-001', externalRef: null,
    });

    const result = processor.reconcile(records, [
      { recordId: 'REC-001', externalRef: 'EXT-1', status: 'CONF', amount: 2500, date: '20240115', reasonCode: '' },
    ]);

    expect(result.reconciled).toContain('REC-001');
    expect(records.get('REC-001').status).toBe('RECONCILED');
  });

  test('reconciles REJC as DISCREPANCY', () => {
    const records = new Map();
    records.set('REC-002', {
      recordId: 'REC-002', orderId: 'ORD-2', clientId: 'C001',
      symbol: 'IBM', quantity: 50, side: 'SELL', amount: 6000,
      commission: 60, tradeDate: new Date(), settlementDate: new Date(),
      status: 'UPLOADED', batchId: 'B-001', externalRef: null,
    });

    const result = processor.reconcile(records, [
      { recordId: 'REC-002', externalRef: 'EXT-2', status: 'REJC', amount: 6000, date: '20240115', reasonCode: 'AMOUNT_MISMATCH' },
    ]);

    expect(result.discrepancies).toContain('REC-002');
    expect(records.get('REC-002').status).toBe('DISCREPANCY');
  });

  test('skips PEND status', () => {
    const records = new Map();
    records.set('REC-003', {
      recordId: 'REC-003', orderId: 'ORD-3', clientId: 'C001',
      symbol: 'ORCL', quantity: 200, side: 'BUY', amount: 3000,
      commission: 30, tradeDate: new Date(), settlementDate: new Date(),
      status: 'UPLOADED', batchId: 'B-001', externalRef: null,
    });

    const result = processor.reconcile(records, [
      { recordId: 'REC-003', externalRef: 'EXT-3', status: 'PEND', amount: 3000, date: '20240115', reasonCode: '' },
    ]);

    expect(result.skipped).toContain('REC-003');
  });
});
