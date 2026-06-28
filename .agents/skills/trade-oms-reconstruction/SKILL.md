---
name: trade-oms-reconstruction
description: Reconstruct business logic and requirements for the securities trade order management system from its legacy source. Use when the user asks to understand, document, reverse-engineer, or extract business rules from this codebase, or mentions trade, order, settlement, commission, pricing, risk, derivative, OMS. This is a domain-tuned wrapper over the generic legacy-code-reconstruction skill, pre-loaded with this project's discovered domain profile.
---

# securities trade order management — Business Logic Reconstruction (domain-tuned)

Auto-generated companion skill. It pins this project's **domain profile** so the generic
pipeline runs tuned to securities trade order management without re-deriving the domain each time.

> Method lives in the generic skill: `../legacy-code-reconstruction/SKILL.md`.
> This file only carries the domain adaptation. Regenerate after big refactors with
> `../legacy-code-reconstruction/tools/generate-companion-skill.sh domain-profile.env`.

## Discovered domain profile

| Field | Value |
|-------|-------|
| Domain | securities trade order management |
| Root package | com.bigcorp |
| Core entities | TradeOrder,Client,SettlementRecord,Notification,RiskOrder,DerivativeOrder |
| Compute verbs | calculate,price,commission,convert,settle,assess,evaluate |
| High-stakes verbs | setstatus,reject,approve,fill,charge,dispatch,send,persist,save |
| Critical modules | rules,pricing,risk,settlement,orderengine,audit,derivatives |

## How to run (tuned)

```bash
GEN=../legacy-code-reconstruction/references/joern-scripts
# domain-profile.env (shipped beside this skill) tunes scripts 13-16 automatically:
cp domain-profile.env "$REPO/joern-workspace/domain-profile.env"
bash "$GEN/run-all.sh" "$REPO" "$REPO/joern-workspace/project.cpg" "$REPO/joern-workspace/output"
```

Or pass the params directly to individual scripts, e.g.:
```bash
joern --script "$GEN/13-critical-value-flows.sc" \
  --param cpgFile="$REPO/joern-workspace/project.cpg" \
  --param computeVerbs="calculate,price,commission,convert,settle,assess,evaluate"
```

Then follow Stage 2 / Stage 3 of the generic skill, treating **TradeOrder,Client,SettlementRecord,Notification,RiskOrder,DerivativeOrder**
as the spec's central value-objects.
