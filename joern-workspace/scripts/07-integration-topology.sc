// 07-integration-topology.sc — Map inter-module communication topology
// Traces JMS queues, SOAP endpoints, SFTP paths, SMTP, HTTP — the "wiring diagram"

@main def main(cpgFile: String = "joern-workspace/bigcorp.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("INTEGRATION TOPOLOGY")
  println("=" * 80)

  // 1. JMS Queue topology
  println("\n--- JMS QUEUES ---")
  val queueLiterals = cpg.literal.l.filter { lit =>
    lit.code.contains("BIGCORP") || lit.code.contains(".QUEUE") ||
    lit.code.contains(".ORDERS") || lit.code.contains(".NOTIFICATIONS") ||
    lit.code.contains(".CONFIRMATIONS") || lit.code.contains(".DERIVATIVES")
  }
  queueLiterals.foreach { lit =>
    println(s"  Queue: ${lit.code}")
    println(s"    Used in: ${lit.method.fullName}")
    println(s"    File: ${lit.file.name.l.headOption.getOrElse("unknown")}")
  }

  // 2. SOAP / Web Service endpoints
  println("\n\n--- SOAP / WEB SERVICE ENDPOINTS ---")
  val soapLiterals = cpg.literal.l.filter { lit =>
    lit.code.contains("wsdl") || lit.code.contains("WSDL") ||
    lit.code.contains("soap") || lit.code.contains("SOAP") ||
    lit.code.contains("endpoint") || lit.code.contains("/service") ||
    lit.code.contains("pricing") && lit.code.contains("http")
  }
  soapLiterals.foreach { lit =>
    println(s"  Endpoint: ${lit.code}")
    println(s"    In: ${lit.method.fullName}")
  }

  // 3. SFTP paths and configuration
  println("\n\n--- SFTP CONFIGURATION ---")
  val sftpLiterals = cpg.literal.l.filter { lit =>
    lit.code.contains("sftp") || lit.code.contains("SFTP") ||
    lit.code.contains("/inbound") || lit.code.contains("/outbound") ||
    lit.code.contains("settlement") && lit.code.contains("/")
  }
  sftpLiterals.foreach { lit =>
    println(s"  Path/Config: ${lit.code}")
    println(s"    In: ${lit.method.fullName}")
  }

  // 4. SMTP / Email configuration
  println("\n\n--- EMAIL / SMTP ---")
  val emailLiterals = cpg.literal.l.filter { lit =>
    lit.code.contains("smtp") || lit.code.contains("SMTP") ||
    lit.code.contains("mail") || lit.code.contains("@") && lit.code.contains(".") ||
    lit.code.contains("email")
  }
  emailLiterals.foreach { lit =>
    println(s"  Email config: ${lit.code}")
    println(s"    In: ${lit.method.fullName}")
  }

  // 5. HTTP / REST endpoints
  println("\n\n--- HTTP ENDPOINTS ---")
  val httpLiterals = cpg.literal.l.filter { lit =>
    (lit.code.contains("http://") || lit.code.contains("https://")) &&
    !lit.code.contains("xmlns")
  }
  httpLiterals.foreach { lit =>
    println(s"  URL: ${lit.code}")
    println(s"    In: ${lit.method.fullName}")
  }

  // 6. Database connection strings
  println("\n\n--- DATABASE CONNECTIONS ---")
  val dbLiterals = cpg.literal.l.filter { lit =>
    lit.code.contains("jdbc:") || lit.code.contains("oracle") ||
    lit.code.contains("hsqldb") || lit.code.contains("HSQLDB")
  }
  dbLiterals.foreach { lit =>
    println(s"  DB: ${lit.code}")
    println(s"    In: ${lit.method.fullName}")
  }

  // 7. Cross-module method calls (trace which modules call which)
  println("\n\n--- CROSS-MODULE DEPENDENCIES ---")
  val modules = List("audit", "common", "demo", "derivatives", "notifications", "orderengine", "pricing", "reporting", "risk", "settlement", "tradedesk")
  modules.foreach { srcModule =>
    val srcMethods = cpg.method.l.filter(_.fullName.contains(s".$srcModule."))
    val calledModules = srcMethods.flatMap { m =>
      m.ast.isCall.l.map(_.methodFullName).filter(_.startsWith("com.bigcorp.")).flatMap { called =>
        modules.find(mod => called.contains(s".$mod.") && mod != srcModule)
      }
    }.distinct.sorted
    if (calledModules.nonEmpty) {
      println(s"  $srcModule -> ${calledModules.mkString(", ")}")
    }
  }
}
