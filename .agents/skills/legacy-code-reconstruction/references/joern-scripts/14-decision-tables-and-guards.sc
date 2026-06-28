// 14-decision-tables-and-guards.sc — Locate the "brain" methods (where business logic
// concentrates), reconstruct guard->action rules, and resolve enum if-ladders + static-final
// constants into the project's real lookup tables (whatever they are — rate tables, tier maps,
// routing tables, dosage bands...).
//
// DOMAIN-NEUTRAL: the "domain getters" used in conditions are DERIVED from the code (the entity
// accessors that actually appear in branch conditions), not hardcoded. High-stakes action verbs
// are a PARAM (stakesVerbs) defaulting to a generic set; tune from the domain profile.
//
// Goes beyond "count control structures": it rebuilds the actual decision/lookup TABLES.

@main def main(cpgFile: String = "workspace.cpg",
               stakesVerbs: String = "setstatus,setstate,reject,deny,block,approve,authorize,persist,save,insert,update,send,dispatch,publish,export,charge") = {
  importCpg(cpgFile)
  println("=" * 80); println("DECISION TABLES & GUARD ANALYSIS"); println("=" * 80)

  // Derive the domain getters: accessor calls (getX/isX) that actually appear inside branch
  // conditions — i.e. the entity attributes this project branches on. No domain assumed.
  val domainGetters: Set[String] = cpg.controlStructure.condition.ast.isCall.name
    .filter(_.matches("(get|is)[A-Z].*")).l
    .groupBy(identity).view.mapValues(_.size).toList.sortBy(-_._2).take(15).map(_._1).toSet

  // Build a static-final constant map: NAME -> initializer, so we can resolve `return RATE_X`.
  val constMap: Map[String, String] = cpg.assignment.code(".*[.A-Z][A-Z_]{2,} *=.*").code.l.flatMap { code =>
    val parts = code.split("=", 2)
    if (parts.length == 2) {
      val lhs = parts(0).trim.split("[ .]").last
      Some(lhs -> parts(1).trim.stripSuffix(";").trim)
    } else None
  }.toMap

  // 1. Decision density ranking — find the spec-bearing methods.
  println("\n--- DECISION-DENSITY RANKING (top business-logic methods) ---")
  cpg.method.filterNot(_.name.startsWith("<")).map { m =>
    val cs = m.controlStructure.l
    val ifs = cs.count(c => c.controlStructureType == "IF" || c.controlStructureType == "SWITCH")
    val condText = cs.flatMap(_.condition.code).mkString(" ")
    val getters = domainGetters.filter(condText.contains)
    (m.fullName, ifs, getters.toList.sorted, ifs + getters.size)
  }.l.filter(_._2 > 0).sortBy(-_._4).take(15).foreach { case (fn, ifs, g, _) =>
    println(f"  ifs=$ifs%2d  ${if (g.nonEmpty) "[" + g.mkString(",") + "]" else ""}%-30s $fn")
  }

  // 2. Guard -> action: IF conditions whose body performs a high-stakes action (verbs from param).
  println("\n\n--- GUARD -> ACTION (reconstructed decision rules) ---")
  val stakes = stakesVerbs.split(",").map(_.trim.toLowerCase).filter(_.nonEmpty).toList
  cpg.controlStructure.controlStructureType("IF").flatMap { ifs =>
    val cond = ifs.condition.code.headOption.getOrElse("")
    val inner = ifs.ast.isCall.name.l.filter(n => stakes.exists(n.toLowerCase.contains))
      .filterNot(_.startsWith("<operator>")).distinct
    if (inner.nonEmpty && cond.nonEmpty) Some((ifs.method.typeDecl.name.headOption.getOrElse("?"), cond.take(70), inner.mkString(","))) else None
  }.l.distinct.take(40).foreach { case (cls, cond, act) =>
    println(f"  [$cls%-24s] if ($cond) -> $act")
  }

  // 3. Enum if-ladder -> value table, resolving constant references via constMap.
  println("\n\n--- ENUM/TIER LOOKUP TABLES (condition -> resolved value) ---")
  // Methods that look like lookups: small, return-heavy, branch on equals().
  val lookupMethods = cpg.method.filterNot(_.name.startsWith("<")).filter { m =>
    m.methodReturn.typeFullName.matches("double|float|int|long|java.lang.String") &&
    m.controlStructure.controlStructureType("IF").nonEmpty &&
    m.ast.isCall.name("equals|equalsIgnoreCase").nonEmpty
  }.l.sortBy(_.fullName).take(12)
  lookupMethods.foreach { m =>
    val rows = m.controlStructure.controlStructureType("IF").l.flatMap { ifs =>
      val cond = ifs.condition.code.headOption.getOrElse("?").take(60)
      val ret = ifs.ast.isReturn.sortBy(_.lineNumber.getOrElse(0)).map(_.code).headOption
        .map(_.replaceAll("return|;", "").trim)
      ret.map { r => val resolved = constMap.getOrElse(r.split("[ .]").last, r); (cond, r, resolved) }
    }.distinct
    if (rows.nonEmpty) {
      println(s"\n  ${m.fullName}")
      rows.foreach { case (c, raw, res) => println(f"    if ($c) -> $raw${if (res != raw) s"  = $res" else ""}") }
    }
  }
}
