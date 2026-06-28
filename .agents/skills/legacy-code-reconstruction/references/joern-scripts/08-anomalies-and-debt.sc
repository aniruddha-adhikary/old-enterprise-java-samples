// 08-anomalies-and-debt.sc — Find code smells, inconsistencies, and technical debt
// These are crucial clues for understanding "what the code ACTUALLY does vs what it SHOULD do"

@main def main(cpgFile: String = "workspace.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("ANOMALIES AND TECHNICAL DEBT")
  println("=" * 80)

  // 1. Duplicate constants — same value defined in multiple places
  println("\n--- DUPLICATE CONSTANTS (potential inconsistencies) ---")
  val allLiterals = cpg.literal.l.filter { lit =>
    lit.typeFullName == "java.lang.String" && lit.code.length > 3 && lit.code.length < 60
  }
  val literalsByValue = allLiterals.groupBy(_.code).filter(_._2.size > 1)
  literalsByValue.toList.sortBy(-_._2.size).take(30).foreach { case (value, lits) =>
    val locations = lits.map(_.method.fullName).distinct
    if (locations.size > 1) {
      println(s"\n  Value: $value (${locations.size} locations)")
      locations.foreach(loc => println(s"    - $loc"))
    }
  }

  // 2. Empty catch blocks (swallowed exceptions)
  println("\n\n--- EMPTY CATCH BLOCKS ---")
  cpg.method.l.foreach { m =>
    m.ast.isControlStructure.l.filter(_.controlStructureType == "TRY").foreach { tryBlock =>
      // Look for catch blocks with minimal content
      val catchBlocks = tryBlock.ast.isBlock.l
      catchBlocks.foreach { block =>
        val stmts = block.ast.isCall.l
        if (stmts.isEmpty || stmts.size <= 1) {
          // Minimal or empty catch
          // Just note the method
        }
      }
    }
  }

  // 3. Hardcoded credentials and magic strings
  println("\n\n--- HARDCODED CREDENTIALS AND SENSITIVE VALUES ---")
  val sensitivePatterns = List("password", "passwd", "secret", "token", "api_key", "apikey", "credential")
  cpg.literal.l.filter { lit =>
    val code = lit.code.toLowerCase
    sensitivePatterns.exists(code.contains)
  }.foreach { lit =>
    println(s"  Sensitive: ${lit.code}")
    println(s"    In: ${lit.method.fullName}")
  }

  // 4. System.out.println and System.err.println (should be proper logging)
  println("\n\n--- CONSOLE OUTPUT (should be logging) ---")
  val consoleCalls = cpg.call.l.filter { c =>
    c.code.contains("System.out") || c.code.contains("System.err")
  }
  println(s"  Total System.out/err calls: ${consoleCalls.size}")
  consoleCalls.take(10).foreach { c =>
    println(s"    ${c.code.take(80)} in ${c.method.fullName}")
  }

  // 5. TODO/FIXME/HACK/XXX/JIRA comments (embedded in string literals or comments)
  println("\n\n--- TODO/FIXME/JIRA MARKERS ---")
  val markerLiterals = cpg.literal.l.filter { lit =>
    val code = lit.code.toUpperCase
    code.contains("TODO") || code.contains("FIXME") || code.contains("HACK") ||
    code.contains("XXX") || code.contains("JIRA") || code.contains("WORKAROUND") ||
    code.contains("TEMPORARY") || code.contains("DEPRECATED")
  }
  markerLiterals.foreach { lit =>
    println(s"  ${lit.code}")
    println(s"    In: ${lit.method.fullName}")
  }

  // 6. Inconsistent null checking patterns
  println("\n\n--- NULL CHECK PATTERNS ---")
  val nullChecks = cpg.call.l.filter { c =>
    c.name == "<operator>.equals" && c.argument.isLiteral.l.exists(_.code == "null")
  }
  println(s"  Total null checks: ${nullChecks.size}")

  // 7. Duplicated logic across modules (same method signatures in different classes)
  println("\n\n--- POTENTIAL DUPLICATED LOGIC ---")
  val methodSigs = cpg.method.l.filterNot(_.name.startsWith("<")).map { m =>
    (m.name, m.parameter.l.filterNot(_.name == "this").map(_.typeFullName).mkString(","), m.fullName)
  }
  val groupedSigs = methodSigs.groupBy(t => (t._1, t._2)).filter(_._2.size > 1)
  groupedSigs.toList.sortBy(-_._2.size).take(20).foreach { case ((name, params), impls) =>
    val classes = impls.map(_._3).distinct
    if (classes.size > 1 && !name.startsWith("get") && !name.startsWith("set") && !name.startsWith("is") &&
        name != "toString" && name != "init" && name != "destroy" && name != "main" &&
        name != "doGet" && name != "doPost" && name != "doFilter") {
      println(s"\n  ${name}(${params}):")
      classes.foreach(c => println(s"    - $c"))
    }
  }

  // 8. Dead code — methods never called
  println("\n\n--- POTENTIALLY DEAD METHODS (no callers found in CPG) ---")
  val allMethodNames = cpg.method.l.filterNot(_.name.startsWith("<")).map(_.name).distinct
  val calledMethodNames = cpg.call.l.map(_.name).distinct.toSet
  val uncalledMethods = cpg.method.l.filterNot(_.name.startsWith("<")).filter { m =>
    !calledMethodNames.contains(m.name) &&
    m.name != "main" && m.name != "doGet" && m.name != "doPost" && m.name != "doFilter" &&
    m.name != "init" && m.name != "destroy" && m.name != "onMessage" &&
    !m.name.startsWith("get") && !m.name.startsWith("set") && !m.name.startsWith("is")
  }
  uncalledMethods.sortBy(_.fullName).foreach { m =>
    println(s"  ${m.fullName}")
  }
}
