import { BaseRule } from '../Rule';
import { RuleContext, setAttribute, rejectContext } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-005: Circuit breaker — kills trading for specific clients
// Priority 118, Category: CircuitBreaker, Behavior: REJECT
// Condition: CLIENTS.KILL_SWITCH == 'Y' (case-insensitive, trimmed)
// Missing column or DB error defaults to 'N' (allow)
export class ClientKillSwitchRule extends BaseRule {
  name = 'ClientKillSwitchRule';
  priority = 118;
  category = 'CircuitBreaker';
  failOpen = true;

  evaluate(ctx: RuleContext): RuleResult {
    try {
      const killSwitch = (ctx.client.killSwitch ?? 'N').trim().toUpperCase();

      setAttribute(ctx, 'kill_switch_checked', true);

      if (killSwitch === 'Y') {
        rejectContext(ctx, `Client ${ctx.client.clientId} kill switch is active`);
        return 'FAIL';
      }

      return 'PASS';
    } catch {
      // Missing column or DB error defaults to 'N' (allow)
      setAttribute(ctx, 'kill_switch_checked', true);
      return 'PASS';
    }
  }
}
