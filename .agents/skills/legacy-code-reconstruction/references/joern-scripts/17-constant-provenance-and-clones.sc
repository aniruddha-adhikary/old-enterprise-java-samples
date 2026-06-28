// 17-constant-provenance-and-clones.sc — Map where each business constant lives (provenance &
// drift), and find near-duplicate business-logic methods (copy-paste-and-tweak rules where one
// copy may carry a bug the other fixed).

@main def main(cpgFile: String = "workspace.cpg") = {
  importCpg(cpgFile)
  println("=" * 80); println("CONSTANT PROVENANCE & NEAR-DUPLICATE LOGIC"); println("=" * 80)

  // Numeric literal value, normalized so "0.01"/"0.010" group together.
  // Keep only BUSINESS-significant values: real decimals (rates/spreads) or large thresholds —
  // drop loop counters / 0 / 1 / small round integers that appear everywhere as noise.
  def norm(code: String): Option[String] =
    try {
      val d = code.toDouble
      val isFractional = d != Math.rint(d)
      val significant = (isFractional && d != 0.0 && d != 1.0) || Math.abs(d) >= 1000
      if (significant) Some(d.toString) else None
    } catch { case _: Throwable => None }

  // 1. Constant provenance + cross-class drift.
  println("\n--- BUSINESS CONSTANT PROVENANCE (value -> classes that hold it) ---")
  val numeric = cpg.literal.filter(_.typeFullName.matches("double|float|int|long")).l
    .flatMap(l => norm(l.code).map(v => (v, l.method.typeDecl.name.headOption.getOrElse("?"), l.code)))
  numeric.groupBy(_._1)
    .map { case (v, rs) => (v, rs.map(_._2).distinct.sorted, rs.map(_._3).distinct) }
    .filter { case (_, classes, _) => classes.size > 1 }       // appears in 2+ classes
    .toList.sortBy(-_._2.size).take(25).foreach { case (v, classes, raws) =>
      val rawNote = if (raws.size > 1) s"  (written as ${raws.mkString("/")})" else ""
      println(f"  $v%-10s in ${classes.size}%2d classes: ${classes.mkString(", ")}$rawNote")
    }
  println("\n  NOTE: same value in many classes can be (a) real drift to unify, or (b) an accidental")
  println("  collision (e.g. an FX rate that equals a buffer multiplier). Confirm by field NAME before unifying.")

  // 2. Near-duplicate method fingerprinting (Jaccard over call-names + numeric literals + operators).
  println("\n\n--- NEAR-DUPLICATE BUSINESS METHODS (0.45 <= similarity < 1.0) ---")
  def fp(m: io.shiftleft.codepropertygraph.generated.nodes.Method): Set[String] =
    (m.call.name.filterNot(_.startsWith("<operator>")).map("c:" + _).l ++
     m.ast.isLiteral.filter(_.typeFullName.matches("double|int|long|float")).code.map("n:" + _).l ++
     m.ast.isCall.name.filter(_.startsWith("<operator>")).map("o:" + _).l).toSet
  // Restrict to non-trivial methods to avoid CRUD shells; require some numeric content.
  val cand = cpg.method.filterNot(_.name.startsWith("<"))
    .filter(m => m.ast.isCall.size >= 6).l
    .map(m => (m, fp(m))).filter(_._2.count(_.startsWith("n:")) >= 1)
  val pairs = for {
    i <- cand.indices.iterator
    j <- (i + 1 until cand.size).iterator
    a = cand(i); b = cand(j)
    inter = (a._2 intersect b._2).size.toDouble
    union = (a._2 union b._2).size
    sim = if (union == 0) 0.0 else inter / union
    if sim >= 0.45 && sim < 1.0 && a._1.name != "<init>"
  } yield (sim, a._1, b._1, a._2 -- b._2, b._2 -- a._2)
  pairs.toList.sortBy(-_._1).take(12).foreach { case (sim, a, b, onlyA, onlyB) =>
    println(f"\n  sim=$sim%.2f  ${a.name} <~> ${b.name}")
    println(s"    ${a.typeDecl.name.headOption.getOrElse("?")}  vs  ${b.typeDecl.name.headOption.getOrElse("?")}")
    val diff = (onlyA.map("A:" + _) ++ onlyB.map("B:" + _)).filterNot(_.contains("o:")).take(6)
    if (diff.nonEmpty) println(s"    DIFF: ${diff.mkString(", ")}")
  }
}
