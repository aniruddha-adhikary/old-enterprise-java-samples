// 09-jira-references.sc — Extract all JIRA ticket references from the codebase
// Provides traceability between code and issue tracker

@main def main(cpgFile: String = "joern-workspace/bigcorp.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("JIRA REFERENCE EXTRACTION")
  println("=" * 80)

  // Find all string literals containing JIRA references
  val jiraPattern = "JIRA-\\d+"
  val jiraLiterals = cpg.literal.l.filter { lit =>
    lit.code.matches(".*JIRA-\\d+.*")
  }

  println("\n--- JIRA REFERENCES BY TICKET ---")
  val byTicket = jiraLiterals.groupBy { lit =>
    val m = jiraPattern.r.findFirstIn(lit.code).getOrElse("UNKNOWN")
    m
  }

  byTicket.toList.sortBy(_._1).foreach { case (ticket, lits) =>
    println(s"\n  $ticket:")
    lits.foreach { lit =>
      println(s"    Context: ${lit.code.take(120)}")
      println(s"    In: ${lit.method.fullName}")
      println(s"    File: ${lit.file.name.l.headOption.getOrElse("unknown")}")
    }
  }

  // Also find REG-NNNN regulatory references
  println("\n\n--- REGULATORY REFERENCES (REG-NNNN) ---")
  val regLiterals = cpg.literal.l.filter { lit =>
    lit.code.matches(".*REG-\\d+-\\d+.*")
  }
  regLiterals.foreach { lit =>
    val ref = "REG-\\d+-\\d+".r.findFirstIn(lit.code).getOrElse("UNKNOWN")
    println(s"\n  $ref:")
    println(s"    Context: ${lit.code.take(120)}")
    println(s"    In: ${lit.method.fullName}")
  }

  // Find WAVE-N references (evolution markers)
  println("\n\n--- WAVE/ERA REFERENCES ---")
  val waveLiterals = cpg.literal.l.filter { lit =>
    lit.code.matches("(?i).*wave.*\\d+.*") || lit.code.matches(".*circa.*\\d+.*")
  }
  waveLiterals.foreach { lit =>
    println(s"  ${lit.code.take(120)}")
    println(s"    In: ${lit.method.fullName}")
  }
}
