import { Rule, RuleContext, rejectContext } from '../rule-engine';

/**
 * ClientKillSwitchRule (priority 118, REG-2011-002)
 * Per-client kill switch for rapid risk response.
 * Added after the 2011 flash crash alongside MarketHaltRule.
 */
export class ClientKillSwitchRule implements Rule {
  name = 'ClientKillSwitchRule';
  priority = 118;
  active = true;

  private killedClients: Set<string>;

  constructor(killedClients?: Set<string>) {
    this.killedClients = killedClients || new Set();
  }

  addKilledClient(clientId: string): void {
    this.killedClients.add(clientId);
  }

  removeKilledClient(clientId: string): void {
    this.killedClients.delete(clientId);
  }

  evaluate(ctx: RuleContext): boolean {
    ctx.attributes.set('kill_switch_checked', true);

    if (this.killedClients.has(ctx.client.clientId)) {
      rejectContext(ctx, `Client ${ctx.client.clientId} is kill-switched (REG-2011-002)`);
      ctx.messages.push(`Order rejected: client kill switch active for ${ctx.client.clientId}`);
      return false;
    }

    return true;
  }
}
