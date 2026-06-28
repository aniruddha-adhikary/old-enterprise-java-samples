// 04-data-flows.sc — Trace data flows: order lifecycle, message paths, DB interactions
// Maps the complete journey of a trade order through the system

@main def main(cpgFile: String = "joern-workspace/bigcorp.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("DATA FLOW ANALYSIS")
  println("=" * 80)

  // 1. Entry points — where do orders enter the system?
  println("\n--- ENTRY POINTS (Servlets, JMS Listeners, Main methods) ---")
  cpg.method.l.filter { m =>
    m.name == "doGet" || m.name == "doPost" || m.name == "onMessage" ||
    m.name == "main" || m.name == "processRequest" || m.name == "startListening"
  }.foreach { m =>
    println(s"  ${m.fullName}")
    println(s"    File: ${m.filename}")
    // What does this entry point call?
    val directCalls = m.ast.isCall.l.filterNot(_.name.startsWith("<operator>")).map(_.name).distinct.sorted
    println(s"    Calls: ${directCalls.mkString(", ")}")
  }

  // 2. JMS message flow — queue names and producers/consumers
  println("\n\n--- JMS MESSAGE QUEUES ---")
  cpg.literal.l.filter { lit =>
    lit.code.contains("BIGCORP") || lit.code.contains("QUEUE") || lit.code.contains("queue")
  }.foreach { lit =>
    println(s"  Queue: ${lit.code}")
    println(s"    Used in: ${lit.method.fullName}")
  }

  // 3. Database interactions — SQL patterns
  println("\n\n--- DATABASE OPERATIONS ---")
  val sqlLiterals = cpg.literal.l.filter { lit =>
    val code = lit.code.toUpperCase
    code.contains("SELECT") || code.contains("INSERT") || code.contains("UPDATE") ||
    code.contains("DELETE") || code.contains("CREATE TABLE")
  }
  sqlLiterals.foreach { lit =>
    println(s"  SQL: ${lit.code.take(150)}")
    println(s"    In method: ${lit.method.fullName}")
  }

  // 4. Method call chains — trace order processing
  println("\n\n--- CALL CHAINS FROM ORDER SUBMISSION ---")
  val submitMethods = cpg.method.l.filter { m =>
    m.name.toLowerCase.contains("submit") || m.name.toLowerCase.contains("process")
  }
  submitMethods.foreach { m =>
    println(s"\n  Chain from: ${m.fullName}")
    val calls = m.ast.isCall.l.filterNot(_.name.startsWith("<operator>")).map(_.name).distinct
    calls.foreach(c => println(s"    -> $c"))
  }

  // 5. External service calls (SOAP, SFTP, SMTP, HTTP)
  println("\n\n--- EXTERNAL SERVICE INTERACTIONS ---")
  val externalCalls = cpg.call.l.filter { c =>
    c.code.contains("SOAP") || c.code.contains("sftp") || c.code.contains("SFTP") ||
    c.code.contains("smtp") || c.code.contains("SMTP") || c.code.contains("HttpURLConnection") ||
    c.code.contains("Transport.send") || c.code.contains("MimeMessage") ||
    c.methodFullName.contains("jcraft") || c.methodFullName.contains("javax.mail")
  }
  externalCalls.take(30).foreach { c =>
    println(s"  ${c.code.take(100)}")
    println(s"    In: ${c.method.fullName}")
  }

  // 6. File I/O — settlement files, config files
  println("\n\n--- FILE I/O PATTERNS ---")
  val fileOps = cpg.call.l.filter { c =>
    c.methodFullName.contains("FileWriter") || c.methodFullName.contains("FileOutputStream") ||
    c.methodFullName.contains("FileInputStream") || c.methodFullName.contains("FileReader") ||
    c.methodFullName.contains("BufferedWriter") || c.methodFullName.contains("PrintWriter")
  }
  fileOps.take(20).foreach { c =>
    println(s"  ${c.code.take(100)}")
    println(s"    In: ${c.method.fullName}")
  }

  // 7. Configuration loading patterns
  println("\n\n--- CONFIGURATION LOADING ---")
  val configCalls = cpg.call.l.filter { c =>
    c.name == "getProperty" || c.name == "getResourceAsStream" ||
    c.name == "load" && c.methodFullName.contains("Properties")
  }
  configCalls.take(20).foreach { c =>
    val args = c.argument.isLiteral.l.map(_.code)
    if (args.nonEmpty) {
      println(s"  Config key: ${args.mkString(", ")}")
      println(s"    In: ${c.method.fullName}")
    }
  }
}
