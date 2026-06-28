// 16-operation-sequence.sc — Reconstruct the ORDERED algorithm of core business transactions
// by sorting calls by source line/order and classifying them into lifecycle phases. This is the
// step-by-step recipe the rewrite must reproduce.
//
// FOR RECONSTRUCTION (not bug-hunting): it also surfaces ordering facts that are easy to get
// wrong in a rewrite — the order of charge/FX/persist/status steps, and values that are computed
// then never consumed (a candidate incomplete/dead feature). These are NOTES to record in the
// spec (the algorithm, plus knownBugs[]/dead-feature with a preserve-vs-complete decision), not
// defects to fix here.
//
// Param: entry = regex selecting the transaction method(s) to reconstruct. If empty, the script
// picks the methods with the most domain calls as entry points.

// computeVerbs: domain computation verbs (from the domain profile) that mark the COMPUTE phase —
// e.g. trading: calculate,price,commission; healthcare: dose,score; logistics: route,rate.
@main def main(cpgFile: String = "workspace.cpg", entry: String = "",
               computeVerbs: String = "calculate,compute,apply,derive,determine,assess,score,price,convert,rate") = {
  importCpg(cpgFile)
  val cverbs = computeVerbs.split(",").map(_.trim.toLowerCase).filter(_.nonEmpty)
  println("=" * 80); println("OPERATION-SEQUENCE RECONSTRUCTION"); println("=" * 80)
  println(s"  compute verbs: ${cverbs.mkString(", ")}")

  // Phase classifier: neutral lifecycle phases (no domain assumed). The COMPUTE phase is keyed
  // off the domain compute verbs; everything else is generic structural vocabulary.
  def phase(n: String): String = {
    val s = n.toLowerCase
    if (s.matches(".*(unmarshal|parse|find|load|fromxml|read|receive|fetch).*")) "1-INGEST"
    else if (s.matches(".*(evaluate|validate|check|rule|runrules|verify).*"))    "2-VALIDATE"
    else if (cverbs.exists(v => s.contains(v)))                                  "3-COMPUTE"
    else if (s.matches(".*(approve|reject|deny|authorize|decide).*"))            "4-DECIDE"
    else if (s.matches(".*(setstatus|setstate).*"))                             "5-STATE"
    else if (s.matches(".*(save|persist|insert|update|store|executeupdate).*")) "6-PERSIST"
    else if (s.matches(".*(send|notify|dispatch|upload|publish|emit|confirm).*"))"7-DISPATCH"
    else ""
  }

  val entries =
    if (entry.nonEmpty) cpg.method.fullName(entry).l
    else cpg.method.filterNot(_.name.startsWith("<"))
           .map(m => (m, m.call.filterNot(_.name.startsWith("<operator>")).map(c => phase(c.name)).count(_.nonEmpty)))
           .l.filter(_._2 >= 5).sortBy(-_._2).take(3).map(_._1)

  entries.foreach { m =>
    println(s"\n#### ${m.fullName}")
    val seq = m.call.filterNot(_.name.startsWith("<operator>")).filterNot(_.name == "<init>")
      .l.sortBy(c => (c.lineNumber.getOrElse(-1), c.order))
      .map(c => (c.lineNumber.getOrElse(-1), phase(c.name), c.name))
      .filter(_._2.nonEmpty)
    seq.foreach { case (ln, ph, n) => println(f"  $ln%4d  $ph%-18s $n") }

    // Ordering note: state set before PERSIST — capture the actual order in the spec.
    val firstState = seq.find(_._2 == "5-STATE").map(_._1)
    val firstPersist = seq.find(_._2 == "6-PERSIST").map(_._1)
    (firstState, firstPersist) match {
      case (Some(s), Some(p)) if s < p => println(f"    ORDERING: state set (line $s) BEFORE persist (line $p) — record this sequence")
      case _ =>
    }
    // Ordering note: COMPUTE happens after PERSIST (value written, then recomputed?) — worth confirming.
    val firstCompute = seq.find(_._2 == "3-COMPUTE").map(_._1)
    (firstPersist, firstCompute) match {
      case (Some(p), Some(c)) if p < c => println(f"    ORDERING: persist (line $p) BEFORE compute (line $c) — confirm intended")
      case _ =>
    }
  }

  // Note 3 (cross-method): context attributes SET in a rule but never READ by a caller —
  // a value computed then dropped (e.g. a discount that never reaches pricing). This is a
  // candidate incomplete/dead feature to RECORD (preserve-vs-complete), not to fix here.
  println("\n\n--- COMPUTED-BUT-UNCONSUMED CONTEXT VALUES (candidate incomplete feature) ---")
  def key(c: io.shiftleft.codepropertygraph.generated.nodes.Call) =
    c.argument.argumentIndex(1).isLiteral.code.headOption.map(_.replaceAll("\"", ""))
  val setKeys = cpg.call.name("setAttribute|put|setProperty").flatMap(key).l.toSet
  val getKeys = cpg.call.name("getAttribute|get|getProperty").flatMap(key).l.toSet
  (setKeys -- getKeys).filter(k => k.matches("(?i).*(discount|bonus|override|rate|fee|adjust|priority).*"))
    .toList.sorted.foreach(k => println(s"  set but never read: $k"))
}
