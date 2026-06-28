// 15-failure-semantics.sc — Capture each rule/calculation's ERROR-HANDLING BEHAVIOR, which is
// part of its specification. Classify try/catch in business-critical code as FAIL-OPEN
// (on error => proceed as if it passed) vs FAIL-CLOSED (on error => block/reject) vs
// SILENT-LOSS (swallow + void return).
//
// FOR RECONSTRUCTION (not bug-hunting): "on a DB error this check returns pass" is a real
// behavioral requirement the rewrite must either preserve or consciously change. Feed each
// result into spec.json — the rule's failureBehavior, and knownBugs[] with a
// preserveForBackwardCompatibility decision where the behavior looks unintended.

// criticalModules: comma list of package/module hints to focus on (from the domain profile).
// Empty (default) = analyze ALL internal code — no domain assumed.
@main def main(cpgFile: String = "workspace.cpg", criticalModules: String = "") = {
  importCpg(cpgFile)
  val domainHints = criticalModules.split(",").map(_.trim.toLowerCase).filter(_.nonEmpty)
  println("=" * 80); println("FAILURE SEMANTICS (fail-open vs fail-closed)"); println("=" * 80)
  println(s"  scope: ${if (domainHints.isEmpty) "all internal code" else domainHints.mkString(", ")}")

  // In scope when it matches a supplied module hint; with no hints, all code (catch blocks only
  // exist in internal methods, so this is already restricted to the project's own code).
  def inDomain(fn: String) = domainHints.isEmpty || domainHints.exists(fn.toLowerCase.contains)

  // Permissive-looking returns/defaults inside a catch suggest fail-open.
  val permissive = Set("true", "false", "0", "0.0", "-1", "-1.0", "null", "n", "\"n\"")
  def isPermissiveReturn(code: String) = {
    val v = code.replaceAll("return|;", "").trim.toLowerCase
    permissive.contains(v) || v.endsWith("_pending") || v.endsWith("default")
  }

  println("\n--- ERROR-HANDLING BEHAVIOR PER CHECK (capture as failureBehavior in the spec) ---")
  cpg.controlStructure.controlStructureType("CATCH").filter(c => inDomain(c.method.fullName)).l
    .sortBy(_.method.fullName).foreach { c =>
      val method = c.method.fullName.split(":").head
      val returns = c.ast.isReturn.code.l
      val rethrows = c.ast.isCall.name.exists(_ == "<operator>.throw")
      val assigns = c.ast.isCall.code.l.filter(s => s.contains("=") && !s.contains("=="))
      val logsOnly = c.ast.isCall.name.exists(_.matches("(?i)(printStackTrace|log|error|warn|info|debug|println).*")) &&
                     returns.isEmpty && !rethrows
      val checkedStamp = c.ast.isCall.code.exists(s => s.matches("(?i).*setAttribute.*checked.*"))

      val label =
        if (rethrows) "FAIL-CLOSED (rethrow)"
        else if (returns.exists(isPermissiveReturn)) "FAIL-OPEN? (permissive return)"
        else if (assigns.exists(a => permissive.exists(p => a.toLowerCase.endsWith(p)))) "FAIL-OPEN? (permissive default)"
        else if (logsOnly) "SILENT-LOSS (log-only, void)"
        else "REVIEW"

      println(s"\n  [$label]  $method")
      if (returns.nonEmpty) println(s"    returns: ${returns.distinct.take(3).mkString(" | ")}")
      val defaults = c.ast.isLiteral.code.l.distinct.filter(_.length < 24).take(6)
      if (defaults.nonEmpty) println(s"    catch literals: ${defaults.mkString(", ")}")
      if (checkedStamp) println(s"    NOTE: marks a \"checked\" attribute even in the failure branch — capture this; the audit trail records the check as run")
    }

  // Fallback chains: methods whose returns cascade (a -> b -> sentinel) — de-facto default rules.
  println("\n\n--- FALLBACK CHAINS (multiple distinct returns in one method) ---")
  cpg.method.filter(m => inDomain(m.fullName)).filter(_.ast.isReturn.size >= 3).l
    .filter(m => m.name.matches("(?i).*(quote|price|rate|exposure|var|lookup|get).*"))
    .sortBy(_.fullName).take(15).foreach { m =>
      val rets = m.ast.isReturn.code.l.distinct.take(5)
      println(s"  ${m.fullName}\n    ${rets.mkString("  |  ")}")
    }
}
