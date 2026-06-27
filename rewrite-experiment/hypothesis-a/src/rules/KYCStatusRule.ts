import { Rule, ClientRepository } from './Rule';
import { RuleContext } from '../domain/RuleContext';

export class KYCStatusRule implements Rule {
  readonly name = 'KYCStatusRule';
  readonly priority = 115;
  readonly category = 'Compliance';

  constructor(private readonly clientRepo: ClientRepository) {}

  isActive(): boolean {
    return true;
  }

  async evaluate(ctx: RuleContext): Promise<boolean> {
    const { order } = ctx;
    if (!order || !order.clientId) return true;

    try {
      const kycStatus = await this.clientRepo.getKycStatus(order.clientId);
      const status = kycStatus || 'PENDING'; // null treated as PENDING
      ctx.setAttribute('kyc_status', status);

      if (status !== 'APPROVED') {
        ctx.reject(
          `KYC status '${status}' does not allow trading -- only APPROVED clients may trade`
        );
        return false;
      }
    } catch {
      ctx.reject('Unable to verify KYC status');
      return false;
    }

    return true;
  }

  async execute(_ctx: RuleContext): Promise<void> {
    // no-op
  }
}
