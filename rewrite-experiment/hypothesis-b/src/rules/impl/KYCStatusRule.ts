import { BaseRule } from '../Rule';
import { RuleContext, setAttribute, rejectContext } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-006: Compliance — KYC status must be APPROVED
// Priority 115, Category: Compliance, Behavior: REJECT
// Missing KYC record treated as PENDING (reject)
export class KYCStatusRule extends BaseRule {
  name = 'KYCStatusRule';
  priority = 115;
  category = 'Compliance';
  failOpen = false;

  private readonly ALLOWED_STATUSES = ['APPROVED'];

  evaluate(ctx: RuleContext): RuleResult {
    const kycStatus = ctx.client.kycStatus ?? 'PENDING';

    setAttribute(ctx, 'kyc_status', kycStatus);

    if (!this.ALLOWED_STATUSES.includes(kycStatus)) {
      rejectContext(ctx, `KYC status '${kycStatus}' not approved for client ${ctx.client.clientId}`);
      return 'FAIL';
    }

    return 'PASS';
  }
}
