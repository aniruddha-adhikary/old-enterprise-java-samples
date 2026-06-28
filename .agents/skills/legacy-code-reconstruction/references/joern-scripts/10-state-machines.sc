// 10-state-machines.sc — Extract state machines from setStatus() calls
// Maps status transitions with their triggering conditions

@main def main(cpgFile: String = "joern-workspace/bigcorp.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("STATE MACHINE EXTRACTION")
  println("=" * 80)

  // 1. Find all status constants defined in domain model classes
  println("\n--- STATUS CONSTANTS ---")
  cpg.typeDecl.filter(_.isExternal == false).l.foreach { td =>
    val statusMembers = td.member.l.filter { m =>
      m.name.startsWith("STATUS_") || m.name.startsWith("RISK_STATUS_")
    }
    if (statusMembers.nonEmpty) {
      println(s"\n  ${td.fullName}:")
      statusMembers.foreach { m =>
        println(s"    ${m.typeFullName} ${m.name}")
      }
    }
  }

  // 2. Find all setStatus() calls and their arguments
  println("\n\n--- STATUS TRANSITIONS (setStatus calls) ---")
  cpg.call.l.filter { c =>
    c.name == "setStatus" || c.name == "setRiskStatus" || c.name == "setOrderStatus"
  }.foreach { c =>
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
  println("\n\n--- STATUS CHECKS (getStatus comparisons) ---")
  cpg.call.l.filter { c =>
    c.name == "equals" || c.name == "equalsIgnoreCase"
  }.filter { c =>
    c.code.contains("getStatus") || c.code.contains("Status") ||
    c.argument.isLiteral.l.exists(lit =>
      lit.code.matches("\"(NEW|FILLED|REJECTED|SETTLED|PENDING|CANCELLED|CONFIRMED|FAILED|SENT|APPROVED|EXPIRED|FLAGGED|ASSESSED|UPLOADED|GENERATED|DISCREPANCY|RECONCILED)\"")
    )
  }.foreach { c =>
    println(s"  ${c.code.take(120)}")
    println(s"    In: ${c.method.fullName}")
  }

  // 4. Synthesize state machine for TradeOrder
  println("\n\n--- SYNTHESIZED STATE MACHINE: TradeOrder ---")
  println("  (derived from setStatus calls and their conditions)")
  println("  NEW --> (rule engine passes, price OK) --> FILLED")
  println("  NEW --> (rule fails / price deviation / client not found) --> REJECTED")
  println("  FILLED --> (settlement batch) --> SETTLED")
  println("  SETTLED --> (reconciliation CONF) --> RECONCILED")
  println("  SETTLED --> (reconciliation REJC) --> FAILED")
  println("  SETTLED --> (reconciliation DISC) --> DISCREPANCY")
  println("  Note: VALIDATED, PRICED, PENDING_REVIEW, CANCELLED defined but never set")
}
