// 07-integration-topology.sc — Map inter-module communication topology
// Traces JMS queues, SOAP endpoints, SFTP paths, SMTP, HTTP — the "wiring diagram"

// rootPackage: pass the value from 00-calibrate, or leave "" to auto-derive it.
@main def main(cpgFile: String = "workspace.cpg", rootPackage: String = "") = {
  importCpg(cpgFile)

  // Auto-derive the root package (longest common dotted prefix) when not supplied.
  val internalNames = cpg.typeDecl.filterNot(_.isExternal).fullName.filter(_.contains(".")).l
  val root: String =
    if (rootPackage.nonEmpty) rootPackage
    else {
      val pkgs = internalNames.map(_.split("\\.").dropRight(1).mkString(".")).filter(_.nonEmpty).distinct
      if (pkgs.isEmpty) "" else {
        val split = pkgs.map(_.split("\\.").toList); val minLen = split.map(_.size).min
        var pref = List.empty[String]; var i = 0; var stop = false
        while (i < minLen && !stop) { val seg = split.head(i)
          if (split.forall(_(i) == seg)) { pref = pref :+ seg; i += 1 } else stop = true }
        pref.mkString(".")
      }
    }
  val rootDepth = if (root.isEmpty) 0 else root.split("\\.").length

  println("=" * 80)
  println("INTEGRATION TOPOLOGY")
  println(s"(root package: '$root')")
  println("=" * 80)

  // 1. Message queue / topic topology — detect dotted UPPER-CASE destination names
  //    generically (e.g. FOO.BAR.ORDERS) rather than a hardcoded brand prefix.
  println("\n--- MESSAGE QUEUES / TOPICS ---")
  val queueLiterals = cpg.literal.l.filter { lit =>
    val s = lit.code.replaceAll("\"", "")
    s.matches("[A-Z][A-Z0-9]*(\\.[A-Z0-9]+){1,}")
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

  // 7. Cross-module method calls (trace which modules call which).
  //    Modules = the package segment directly under the root, derived from the CPG.
  println("\n\n--- CROSS-MODULE DEPENDENCIES ---")
  val modules = internalNames
    .map(_.split("\\."))
    .filter(_.length > rootDepth + 1)
    .map(_(rootDepth))
    .distinct.sorted
  val callPrefix = if (root.isEmpty) "" else root + "."
  modules.foreach { srcModule =>
    val srcMethods = cpg.method.l.filter(_.fullName.contains(s".$srcModule."))
    val calledModules = srcMethods.flatMap { m =>
      m.ast.isCall.l.map(_.methodFullName).filter(_.startsWith(callPrefix)).flatMap { called =>
        modules.find(mod => called.contains(s".$mod.") && mod != srcModule)
      }
    }.distinct.sorted
    if (calledModules.nonEmpty) {
      println(s"  $srcModule -> ${calledModules.mkString(", ")}")
    }
  }
}
