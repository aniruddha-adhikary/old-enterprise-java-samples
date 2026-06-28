// 10-state-machines.sc — Extract state machines from setStatus() calls
// Maps status transitions with their triggering conditions

@main def main(cpgFile: String = "workspace.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("STATE MACHINE EXTRACTION")
  println("=" * 80)

  // 1. Find all status constants defined in domain classes (any STATUS_*/STATE_*/*_STATUS_* field)
  println("\n--- STATUS CONSTANTS ---")
  cpg.typeDecl.filter(_.isExternal == false).l.foreach { td =>
    val statusMembers = td.member.l.filter { m =>
      m.name.matches("(STATUS|STATE)_.*") || m.name.matches(".*_STATUS_.*")
    }
    if (statusMembers.nonEmpty) {
      println(s"\n  ${td.fullName}:")
      statusMembers.foreach { m =>
        println(s"    ${m.typeFullName} ${m.name}")
      }
    }
  }

  // Match any setter whose name looks like a status mutator (setStatus, setRiskStatus,
  // setOrderState, ...), so this is not tied to one entity's API.
  def isStatusSetter(n: String) = n.matches("set([A-Z][a-z]+)?(Status|State)")

  // 2. Find all status-setter calls and their arguments
  println("\n\n--- STATUS TRANSITIONS (setStatus/setState calls) ---")
  cpg.call.l.filter(c => isStatusSetter(c.name)).foreach { c =>
    val method = c.method.fullName
    val file = c.file.name.l.headOption.getOrElse("unknown")

    // Try to get the argument (what status is being set)
    val args = c.argument.l.drop(1) // skip 'this'
    val argStr = args.map { a =>
      if (a.isLiteral) a.code
      else a.code.take(80)
    }.mkString(", ")

    // Try to find the enclosing conditional (what triggers this transition)
    val enclosingIfs = c.inAst.isControlStructure.l.filter(_.controlStructureType == "IF")
    val condition = enclosingIfs.headOption.map(_.code.take(120)).getOrElse("(unconditional)")

    println(s"\n  Transition: ${c.code.take(100)}")
    println(s"    Value: $argStr")
    println(s"    Condition: $condition")
    println(s"    Method: $method")
    println(s"    File: $file")
  }

  // 3. Find status comparisons (reads of status for decision making)
  // Derive the actual status vocabulary from the values passed to status setters,
  // instead of hardcoding a fixed enum list.
  val statusValues: Set[String] = cpg.call.filter(c => isStatusSetter(c.name))
    .argument.argumentIndex(1).isLiteral.code.l
    .map(_.replaceAll("\"", "")).filter(_.matches("[A-Z][A-Z_]+")).toSet

  println("\n\n--- STATUS CHECKS (status comparisons) ---")
  cpg.call.l.filter { c =>
    c.name == "equals" || c.name == "equalsIgnoreCase"
  }.filter { c =>
    c.code.contains("getStatus") || c.code.contains("Status") || c.code.contains("State") ||
    c.argument.isLiteral.code.exists(v => statusValues.contains(v.replaceAll("\"", "")))
  }.foreach { c =>
    println(s"  ${c.code.take(120)}")
    println(s"    In: ${c.method.fullName}")
  }

  // 4. Synthesize a state machine PER ENTITY from observed setStatus transitions.
  //    Group transitions by the declaring type of the status setter; list reachable
  //    target states; flag STATUS_* constants that are defined but never actually set.
  println("\n\n--- SYNTHESIZED STATE MACHINES (per entity, derived) ---")
  val transitionsByType = cpg.call.filter(c => isStatusSetter(c.name)).l
    .flatMap { c =>
      val tgt = c.argument.argumentIndex(1).isLiteral.code.headOption
        .orElse(c.argument.argumentIndex(1).code.headOption).map(_.replaceAll("\"", ""))
      // best-effort owning entity: static type of the receiver the setter is called on
      val recv = c.argument.argumentIndex(0)
      val owner = (recv.isIdentifier.typeFullName.l ++ recv.isCall.typeFullName.l)
        .headOption.map(_.split("\\.").last)
        .getOrElse(c.method.typeDecl.name.headOption.getOrElse("?"))
      tgt.map(t => (owner, t))
    }.distinct
  transitionsByType.groupBy(_._1).toList.sortBy(_._1).foreach { case (entity, ts) =>
    val targets = ts.map(_._2).distinct.sorted
    println(s"\n  $entity: reachable states -> ${targets.mkString(", ")}")
  }

  // Defined-but-never-set: constant fields whose suffix value is never a transition target.
  val setValues = transitionsByType.map(_._2).toSet
  println("\n  Status constants DEFINED but never SET (candidate dead states):")
  cpg.typeDecl.filterNot(_.isExternal).l.foreach { td =>
    td.member.l.filter(m => m.name.matches("(STATUS|STATE)_.*") || m.name.matches(".*_STATUS_.*"))
      .foreach { m =>
        val suffix = m.name.split("_").last
        if (!setValues.exists(v => v.endsWith(suffix) || v == m.name))
          println(s"    ${td.name}.${m.name}")
      }
  }
  println("  (verify against source — value may be set via a constant reference the CPG sees as non-literal)")
}
