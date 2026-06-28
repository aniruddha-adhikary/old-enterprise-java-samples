// 18-entity-mutation-surface.sc — Reconstruct each entity's true (undocumented) lifecycle from
// ALL field writes across the system: which module mutates which entity (encapsulation leaks),
// which fields are co-written (atomic invariants), and which are write-only/read-only.
//
// Complements scripts 10 (setStatus) and 12 (rule-context) by going entity-wide.

@main def main(cpgFile: String = "workspace.cpg", rootPackage: String = "") = {
  importCpg(cpgFile)

  // Derive root package if not given (longest common prefix of internal types).
  val internalNames = cpg.typeDecl.filterNot(_.isExternal).fullName.filter(_.contains(".")).l
  val root =
    if (rootPackage.nonEmpty) rootPackage
    else {
      val pkgs = internalNames.map(_.split("\\.").dropRight(1).mkString(".")).filter(_.nonEmpty).distinct
      if (pkgs.isEmpty) "" else {
        val sp = pkgs.map(_.split("\\.").toList); val mn = sp.map(_.size).min
        var pre = List.empty[String]; var i = 0; var stop = false
        while (i < mn && !stop) { val s = sp.head(i); if (sp.forall(_(i) == s)) { pre = pre :+ s; i += 1 } else stop = true }
        pre.mkString(".")
      }
    }
  val rootDepth = if (root.isEmpty) 0 else root.split("\\.").length
  def moduleOf(fqn: String): String = { val p = fqn.split("\\."); if (p.length > rootDepth + 1) p(rootDepth) else "?" }

  println("=" * 80); println(s"ENTITY MUTATION SURFACE (root '$root')"); println("=" * 80)

  // Collect (entity, field, writingModule, method) for every setX call.
  // Receiver type via typed steps (identifier OR fluent call return).
  case class W(entity: String, field: String, module: String, method: String)
  val writes = cpg.call.name("set[A-Z].*").l.flatMap { c =>
    val recvType = (c.argument.argumentIndex(0).isIdentifier.typeFullName.l ++
                    c.argument.argumentIndex(0).isCall.typeFullName.l).headOption
    recvType.map { rt =>
      W(rt.split("\\.").last, c.name.stripPrefix("set"),
        moduleOf(c.method.typeDecl.fullName.headOption.getOrElse("?")), c.method.name)
    }
  }.filter(w => internalNames.exists(_.endsWith("." + w.entity)))  // keep internal entities only

  // 1. Mutation surface by module.
  println("\n--- MUTATION SURFACE (entity: writes, by module) ---")
  writes.groupBy(_.entity).toList.sortBy(-_._2.size).foreach { case (e, ws) =>
    val byMod = ws.groupBy(_.module).view.mapValues(_.size).toList.sortBy(-_._2)
    println(f"  $e%-20s writes=${ws.size}%3d  [${byMod.map { case (m, n) => s"$m:$n" }.mkString(", ")}]")
  }

  // 2. Co-mutation groups (>=3 distinct fields of one entity set in one method) = atomic invariants.
  println("\n\n--- CO-MUTATION GROUPS (atomic operations / invariants) ---")
  val hydrators = "(?i).*(unmarshal|fromxml|maps?resultset|tofrom|parse|build|create).*"
  cpg.method.filterNot(_.name.startsWith("<")).l.foreach { m =>
    val fs = m.call.name("set[A-Z].*").l.flatMap { c =>
      (c.argument.argumentIndex(0).isIdentifier.typeFullName.l ++ c.argument.argumentIndex(0).isCall.typeFullName.l)
        .headOption.map(rt => (rt.split("\\.").last, c.name.stripPrefix("set")))
    }
    fs.groupBy(_._1).filter(_._2.size >= 3).foreach { case (e, g) =>
      if (internalNames.exists(_.endsWith("." + e))) {
        val tag = if (m.name.matches(hydrators)) "  [hydrator/deserializer]" else ""
        println(s"  ${m.name}  $e {${g.map(_._2).distinct.sorted.mkString(",")}}$tag")
      }
    }
  }

  // 3. Write-only fields (set, never read via get/is).
  println("\n\n--- WRITE-ONLY ENTITY FIELDS (set, never read) ---")
  val readFields = cpg.call.name("(get|is)[A-Z].*").name.l.map(_.replaceAll("^(get|is)", "")).toSet
  writes.map(w => (w.entity, w.field)).distinct
    .filterNot { case (_, f) => readFields.contains(f) }
    .sortBy(x => (x._1, x._2)).foreach { case (e, f) => println(s"  $e.$f") }
}
