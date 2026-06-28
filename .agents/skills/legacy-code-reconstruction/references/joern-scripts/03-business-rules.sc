// 03-business-rules.sc — Extract business rules: conditions, thresholds, validations
// Identifies rule classes, conditional logic, magic numbers, and business constraints

@main def main(cpgFile: String = "workspace.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("BUSINESS RULES EXTRACTION")
  println("=" * 80)

  // 1. Find all Rule implementations
  println("\n--- RULE CLASSES ---")
  val ruleTypes = cpg.typeDecl.filter(_.isExternal == false).l.filter { td =>
    td.name.contains("Rule") ||
    td.inheritsFromTypeFullName.l.exists(_.contains("Rule")) ||
    td.fullName.contains(".rules.")
  }
  ruleTypes.sortBy(_.fullName).foreach { td =>
    println(s"\n  RULE: ${td.fullName}")
    println(s"    Inherits: ${td.inheritsFromTypeFullName.l.mkString(", ")}")

    // Find the evaluate/execute method (the main rule logic)
    val evalMethods = td.method.l.filter { m =>
      m.name == "evaluate" || m.name == "execute" || m.name == "apply" || m.name == "check"
    }
    evalMethods.foreach { m =>
      println(s"    Entry point: ${m.name}(${m.parameter.l.filterNot(_.name == "this").map(p => s"${p.typeFullName}").mkString(", ")})")

      // Extract conditionals inside the rule
      val conditions = m.ast.isControlStructure.l
      println(s"    Control structures: ${conditions.size}")

      // Extract string comparisons (business logic checks)
      val stringComparisons = m.ast.isCall.l.filter { c =>
        c.name == "equals" || c.name == "equalsIgnoreCase" || c.name == "contains" || c.name == "startsWith"
      }
      if (stringComparisons.nonEmpty) {
        println(s"    String comparisons:")
        stringComparisons.foreach { c =>
          val args = c.argument.isLiteral.l.map(_.code)
          if (args.nonEmpty) {
            println(s"      ${c.code} -> compares with: ${args.mkString(", ")}")
          }
        }
      }

      // Extract numeric comparisons (thresholds)
      val numericOps = m.ast.isCall.l.filter { c =>
        c.name.startsWith("<operator>.") && (
          c.name.contains("greaterThan") || c.name.contains("lessThan") ||
          c.name.contains("greaterEquals") || c.name.contains("lessEquals")
        )
      }
      if (numericOps.nonEmpty) {
        println(s"    Numeric comparisons:")
        numericOps.foreach { c =>
          val literals = c.argument.isLiteral.l.map(_.code)
          if (literals.nonEmpty) {
            println(s"      ${c.code} [threshold values: ${literals.mkString(", ")}]")
          }
        }
      }
    }

    // Find all hardcoded values (magic numbers and strings).
    // Keep ALL numeric literals — single-digit thresholds matter (e.g. T+3 settlement,
    // retry counts). Only trim trivial 1-char strings, not numbers.
    val allLiterals = td.method.ast.isLiteral.l
    val numericLiterals = allLiterals.filter(_.typeFullName != "java.lang.String").map(_.code).distinct
    val stringLiterals = allLiterals.filter(_.typeFullName == "java.lang.String").filter(_.code.length > 1).map(_.code).distinct

    if (numericLiterals.nonEmpty) {
      println(s"    Magic numbers: ${numericLiterals.mkString(", ")}")
    }
    if (stringLiterals.nonEmpty) {
      println(s"    String constants: ${stringLiterals.mkString(", ")}")
    }
  }

  // 2. Find conditional branches that reference domain objects (business decisions)
  println("\n\n--- BUSINESS DECISION POINTS (if/switch on domain fields) ---")
  val bizDecisions = cpg.method.l.flatMap { m =>
    m.ast.isControlStructure.l.filter { cs =>
      cs.code.contains("getStatus") || cs.code.contains("getTier") ||
      cs.code.contains("getSide") || cs.code.contains("getSymbol") ||
      cs.code.contains("getClient") || cs.code.contains("getQuantity") ||
      cs.code.contains("getPrice") || cs.code.contains("getValue")
    }.map(cs => (m, cs))
  }
  bizDecisions.take(50).foreach { case (m, cs) =>
    println(s"\n  In ${m.fullName}:")
    println(s"    Condition: ${cs.code.take(120)}")
  }

  // 3. Find throw statements (rejection/validation logic)
  println("\n\n--- REJECTION/EXCEPTION PATTERNS ---")
  cpg.method.l.foreach { m =>
    val throws = m.ast.isCall.l.filter(_.name == "<operator>.throw")
    if (throws.nonEmpty) {
      println(s"\n  ${m.fullName}:")
      throws.foreach { t =>
        println(s"    throws: ${t.code.take(100)}")
      }
    }
  }

  // 4. Find status transitions (assignments to status fields)
  println("\n\n--- STATUS TRANSITIONS ---")
  cpg.call.l.filter { c =>
    c.name == "setStatus" || c.name == "setOrderStatus" || c.name == "setRiskStatus"
  }.foreach { c =>
    val args = c.argument.isLiteral.l.map(_.code)
    val method = c.method.fullName
    println(s"  ${method}: ${c.code} [values: ${args.mkString(", ")}]")
  }
}
