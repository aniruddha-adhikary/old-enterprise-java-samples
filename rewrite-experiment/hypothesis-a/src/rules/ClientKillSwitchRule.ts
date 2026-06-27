import { Rule, ClientRepository } from './Rule';
import { RuleContext } from '../domain/RuleContext';

export class ClientKillSwitchRule implements Rule {
  readonly name = 'ClientKillSwitchRule';
  readonly priority = 118;
  readonly category = 'CircuitBreaker';

  constructor(private readonly clientRepo: ClientRepository) {}

  isActive(): boolean {
    return true;
  }

  async evaluate(ctx: RuleContext): Promise<boolean> {
    const { order } = ctx;
    if (!order || !order.clientId) return true;

    try {
      const killSwitch = await this.clientRepo.getKillSwitch(order.clientId);
      if (killSwitch.trim().toUpperCase() === 'Y') {
        ctx.reject(
          'Client trading suspended -- kill switch active (REG-2011-002)'
        );
        return false;
      }
    } catch {
      // if column not found or DB error, defaults to 'N' (allow)
    }

    return true;
  }

  async execute(_ctx: RuleContext): Promise<void> {
    // no-op
  }
}
