import { calculateSettlementDate } from '../src/domain/SettlementRecord';

describe('Settlement Date Calculation (T+3)', () => {
  test('adds 3 calendar days to trade date', () => {
    const tradeDate = new Date('2024-01-15T10:00:00Z'); // Monday
    const settlement = calculateSettlementDate(tradeDate);
    expect(settlement.getDate()).toBe(18); // Thursday
  });

  test('does NOT skip weekends (BUG-004 preserved)', () => {
    const friday = new Date('2024-01-19T10:00:00Z'); // Friday
    const settlement = calculateSettlementDate(friday);
    // T+3 = Monday (Jan 22), crossing weekend but NOT skipping it
    expect(settlement.getDate()).toBe(22);
    expect(settlement.getDay()).toBe(1); // Monday
  });

  test('Thursday trade settles on Sunday (BUG-004)', () => {
    const thursday = new Date('2024-01-18T10:00:00Z'); // Thursday
    const settlement = calculateSettlementDate(thursday);
    expect(settlement.getDate()).toBe(21);
    expect(settlement.getDay()).toBe(0); // Sunday - bug preserved
  });

  test('preserves time component', () => {
    const tradeDate = new Date('2024-01-15T14:30:00Z');
    const settlement = calculateSettlementDate(tradeDate);
    expect(settlement.getUTCHours()).toBe(14);
    expect(settlement.getUTCMinutes()).toBe(30);
  });

  test('handles month boundary', () => {
    const jan30 = new Date('2024-01-30T10:00:00Z');
    const settlement = calculateSettlementDate(jan30);
    expect(settlement.getMonth()).toBe(1); // February
    expect(settlement.getDate()).toBe(2);
  });

  test('handles year boundary', () => {
    const dec30 = new Date('2023-12-30T10:00:00Z');
    const settlement = calculateSettlementDate(dec30);
    expect(settlement.getFullYear()).toBe(2024);
    expect(settlement.getMonth()).toBe(0); // January
    expect(settlement.getDate()).toBe(2);
  });
});
