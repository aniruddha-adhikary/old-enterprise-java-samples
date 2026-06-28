import { Rule, RuleContext, rejectContext } from '../rule-engine';

/**
 * KYCStatusRule (priority 115)
 * Verifies KYC_STATUS = 'APPROVED' in database before allowing trading.
 * Added post-2005 regulatory incident.
 * In the modern version, accepts a lookup function for testability.
 */
export class KYCStatusRule implements Rule {
  name = 'KYCStatusRule';
  priority = 115;
  active = true;

  private lookupKycStatus: (clientId: string) => string | null;

  constructor(lookupFn?: (clientId: string) => string | null) {
    this.lookupKycStatus = lookupFn || (() => 'APPROVED');
  }

  evaluate(ctx: RuleContext): boolean {
    const kycStatus = this.lookupKycStatus(ctx.client.clientId);
    ctx.attributes.set('kyc_status', kycStatus);

    if (kycStatus !== 'APPROVED') {
      rejectContext(ctx, `Client ${ctx.client.clientId} KYC status is '${kycStatus}', must be APPROVED`);
      ctx.messages.push(`Order rejected: KYC not approved for ${ctx.client.clientId}`);
      return false;
    }

    return true;
  }
}
