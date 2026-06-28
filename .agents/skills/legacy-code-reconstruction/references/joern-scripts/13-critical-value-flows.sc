// 13-critical-value-flows.sc — Use Joern DATA FLOW (reachableByFlows) to RECONSTRUCT how inputs
// flow into the project's CRITICAL OPERATIONS — the computations, persistence writes, and
// external sends the spec must reproduce — and whether each is preceded by a validation.
//
// DOMAIN-NEUTRAL: this script assumes NO domain. The "critical computation" verbs are a PARAM
// supplied from the project's domain profile (see 00-calibrate's DOMAIN VERBS / CANDIDATE
// CRITICAL OPERATIONS, and references/grounding-on-current-project.md). E.g. trading:
// calculate,price,commission; healthcare: dose,score,eligibility; logistics: route,rate,schedule.
// Persistence and external-send sinks are generic. Default computeVerbs is a generic compute set.
//
// FOR RECONSTRUCTION (not bug-hunting): the output documents WHICH paths compute critical values
// and whether each validates first. When two paths compute the same thing differently, that
// asymmetry IS a business rule to capture in spec.json — flagged preserve-vs-fix.
//
// Params:
//   computeVerbs = comma list of domain computation/decision verbs (from the domain profile).
//   ruleMethods  = method names that count as a validation/decision guard.

@main def main(cpgFile: String = "workspace.cpg",
               computeVerbs: String = "calculate,compute,apply,derive,determine,assess,score,evaluate,process,resolve,convert",
               ruleMethods: String = "evaluate,validate,check,runRules,applyRules,isValid,verify,authorize") = {
  importCpg(cpgFile)
  val guards = ruleMethods.split(",").map(_.trim).filter(_.nonEmpty).toSet
  val verbs = computeVerbs.split(",").map(_.trim.toLowerCase).filter(_.nonEmpty)
  def isCompute(n: String) = { val s = n.toLowerCase; verbs.exists(v => s == v || s.startsWith(v)) }
  def isPersist(n: String) = n.matches("(?i)(execute|executeUpdate|executeQuery|prepareStatement|insert.*|save.*|update.*|store.*|persist.*|write.*)")
  def isSend(n: String)    = n.matches("(?i)(send|upload|put|publish|dispatch|emit|post|transmit).*")

  println("=" * 80); println("CRITICAL-VALUE FLOW RECONSTRUCTION"); println("=" * 80)
  println(s"  compute verbs: ${verbs.mkString(", ")}")

  val computeSinks  = cpg.call.filter(c => isCompute(c.name)).l
  val criticalSinks = (computeSinks ++ cpg.call.filter(c => isPersist(c.name)).l).distinct

  // 1. Critical paths: every method that computes a domain value or persists, and whether it runs
  //    a validation first. Capture BOTH kinds — "validates-first: no" is a documented behavior.
  println("\n--- CRITICAL-VALUE PATHS (validates-before-act?) ---")
  criticalSinks.map(_.method).distinct.sortBy(_.fullName).foreach { m =>
    val validatesFirst = m.ast.isCall.name.exists(guards.contains)
    val sinksHere = criticalSinks.filter(_.method == m).name.distinct.l
    val selfDelegate = sinksHere.forall(_ == m.name)   // ignore trivial overload delegates
    if (!selfDelegate)
      println(f"  validates-first=${if (validatesFirst) "YES" else "NO "}  ${m.fullName}  [does: ${sinksHere.mkString(",")}]")
  }

  // 2. Trace how external input reaches each critical operation — the pipeline to document.
  println("\n\n--- PIPELINE: external input -> critical operation (sampled flows) ---")
  val sources = cpg.call.name("(?i)(getParameter|getQuote|getTextContent|getText|parseDouble|parseInt|nextLine|readLine|getString|getDouble|getInt)")
    .argument ++ cpg.method.name("(?i)(unmarshal.*|parse.*|onMessage|doGet|doPost|handle.*|receive.*)").parameter
  val flows = criticalSinks.argument.reachableByFlows(sources).take(6).l
  if (flows.isEmpty) println("  (no literal-typed flows surfaced — widen sources, or values pass through fields)")
  flows.foreach { f =>
    val elems = f.elements
    println(s"  FLOW: ${elems.headOption.map(_.code.take(40)).getOrElse("?")}  ~~>  ${elems.lastOption.map(_.code.take(50)).getOrElse("?")}")
    println(s"    via: ${elems.map(_.code.take(28)).take(6).mkString(" -> ")}")
  }

  // 3. The most-validated critical method — the canonical pipeline whose validation sequence the
  //    spec should treat as the complete version when paths differ.
  println("\n\n--- CANONICAL (most-validated) CRITICAL METHOD ---")
  criticalSinks.map(_.method).distinct
    .map(m => (m, m.ast.isControlStructure.size + m.ast.isCall.name.count(guards.contains) * 5))
    .sortBy(-_._2).headOption.foreach { case (m, score) =>
      println(s"  ${m.fullName}  (validation/decision score $score)")
      println(s"    checks applied: ${m.ast.isCall.name.filter(guards.contains).distinct.mkString(", ")}")
    }
}
