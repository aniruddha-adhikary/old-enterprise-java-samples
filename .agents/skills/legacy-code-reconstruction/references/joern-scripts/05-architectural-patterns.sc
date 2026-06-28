// 05-architectural-patterns.sc — Identify enterprise patterns, design patterns, integration points
// Detects J2EE patterns: DAO, DTO, Service Locator, Front Controller, Command, etc.

@main def main(cpgFile: String = "workspace.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("ARCHITECTURAL PATTERN ANALYSIS")
  println("=" * 80)

  // 1. DAO Pattern — data access objects
  println("\n--- DAO PATTERN ---")
  cpg.typeDecl.filter(_.isExternal == false).l.filter { td =>
    td.name.contains("DAO") || td.name.contains("Dao")
  }.foreach { td =>
    println(s"\n  DAO: ${td.fullName}")
    td.method.l.filterNot(_.name.startsWith("<")).foreach { m =>
      println(s"    ${m.name}(${m.parameter.l.filterNot(_.name == "this").map(p => s"${p.typeFullName}").mkString(", ")}): ${m.methodReturn.typeFullName}")
      // Find SQL in this method
      m.ast.isLiteral.l.filter(_.code.toUpperCase.matches(".*\\b(SELECT|INSERT|UPDATE|DELETE)\\b.*")).foreach { sql =>
        println(s"      SQL: ${sql.code.take(120)}")
      }
    }
  }

  // 2. DTO / Transfer Object Pattern
  println("\n\n--- DTO / TRANSFER OBJECT PATTERN ---")
  cpg.typeDecl.filter(_.isExternal == false).l.filter { td =>
    td.name.contains("TransferObject") || td.name.contains("DTO") || td.name.contains("Dto")
  }.foreach { td =>
    println(s"\n  DTO: ${td.fullName}")
    td.member.l.foreach { m =>
      println(s"    ${m.typeFullName} ${m.name}")
    }
    // Check for XML serialization methods
    val xmlMethods = td.method.l.filter(m => m.name.contains("Xml") || m.name.contains("xml") || m.name.contains("toXml") || m.name.contains("fromXml") || m.name.contains("serialize") || m.name.contains("deserialize"))
    if (xmlMethods.nonEmpty) {
      println(s"    XML methods: ${xmlMethods.map(_.name).mkString(", ")}")
    }
  }

  // 3. Service Locator Pattern
  println("\n\n--- SERVICE LOCATOR PATTERN ---")
  cpg.typeDecl.filter(_.isExternal == false).l.filter(_.name.contains("ServiceLocator")).foreach { td =>
    println(s"  ${td.fullName}")
    td.method.l.filterNot(_.name.startsWith("<")).foreach { m =>
      println(s"    ${m.name}: ${m.methodReturn.typeFullName}")
    }
    // What services are registered?
    td.method.ast.isLiteral.l.map(_.code).distinct.sorted.foreach { lit =>
      println(s"    Registered key: $lit")
    }
  }

  // 4. Factory Pattern
  println("\n\n--- FACTORY PATTERN ---")
  cpg.typeDecl.filter(_.isExternal == false).l.filter(_.name.contains("Factory")).foreach { td =>
    println(s"\n  ${td.fullName}")
    println(s"    Inherits: ${td.inheritsFromTypeFullName.l.mkString(", ")}")
    td.method.l.filterNot(_.name.startsWith("<")).foreach { m =>
      println(s"    ${m.name}(): ${m.methodReturn.typeFullName}")
    }
  }

  // 5. Command Pattern
  println("\n\n--- COMMAND PATTERN ---")
  cpg.typeDecl.filter(_.isExternal == false).l.filter { td =>
    td.name.contains("Command") || td.inheritsFromTypeFullName.l.exists(_.contains("Command"))
  }.foreach { td =>
    println(s"\n  ${td.fullName}")
    println(s"    Inherits: ${td.inheritsFromTypeFullName.l.mkString(", ")}")
    td.method.l.filter(_.name == "execute").foreach { m =>
      val calls = m.ast.isCall.l.filterNot(_.name.startsWith("<operator>")).map(_.name).distinct
      println(s"    execute() calls: ${calls.mkString(", ")}")
    }
  }

  // 6. Front Controller Pattern
  println("\n\n--- FRONT CONTROLLER PATTERN ---")
  cpg.typeDecl.filter(_.isExternal == false).l.filter { td =>
    td.name.contains("FrontController") || td.name.contains("Servlet")
  }.foreach { td =>
    println(s"\n  ${td.fullName}")
    td.method.l.filter(m => m.name == "doGet" || m.name == "doPost" || m.name == "processRequest" || m.name == "resolveAction").foreach { m =>
      println(s"    ${m.name}:")
      // Find URL mappings or action resolution
      m.ast.isLiteral.l.map(_.code).distinct.sorted.foreach { lit =>
        if (lit.contains("/") || lit.contains(".do") || lit.contains("action")) {
          println(s"      Route/Action: $lit")
        }
      }
    }
  }

  // 7. Filter Chain Pattern (Servlet Filters)
  println("\n\n--- FILTER CHAIN PATTERN ---")
  cpg.typeDecl.filter(_.isExternal == false).l.filter(_.name.contains("Filter")).foreach { td =>
    println(s"\n  ${td.fullName}")
    td.method.l.filter(_.name == "doFilter").foreach { m =>
      val calls = m.ast.isCall.l.filterNot(_.name.startsWith("<operator>")).map(_.name).distinct
      println(s"    doFilter() calls: ${calls.mkString(", ")}")
    }
  }

  // 8. Delegate / Business Delegate Pattern
  println("\n\n--- BUSINESS DELEGATE PATTERN ---")
  cpg.typeDecl.filter(_.isExternal == false).l.filter(_.name.contains("Delegate")).foreach { td =>
    println(s"\n  ${td.fullName}")
    td.method.l.filterNot(_.name.startsWith("<")).foreach { m =>
      println(s"    ${m.name}(${m.parameter.l.filterNot(_.name == "this").map(p => s"${p.typeFullName}").mkString(", ")}): ${m.methodReturn.typeFullName}")
    }
  }

  // 9. Singleton Pattern
  println("\n\n--- SINGLETON PATTERN ---")
  cpg.typeDecl.filter(_.isExternal == false).l.filter { td =>
    td.method.l.exists(m => m.name == "getInstance" || m.name == "instance")
  }.foreach { td =>
    println(s"  ${td.fullName}")
  }

  // 10. Observer/Listener Pattern
  println("\n\n--- LISTENER/OBSERVER PATTERN ---")
  cpg.typeDecl.filter(_.isExternal == false).l.filter { td =>
    td.name.contains("Listener") || td.name.contains("Consumer") ||
    td.inheritsFromTypeFullName.l.exists(t => t.contains("MessageListener") || t.contains("Listener"))
  }.foreach { td =>
    println(s"\n  ${td.fullName}")
    println(s"    Implements: ${td.inheritsFromTypeFullName.l.mkString(", ")}")
    td.method.l.filterNot(_.name.startsWith("<")).foreach { m =>
      println(s"    ${m.name}")
    }
  }
}
