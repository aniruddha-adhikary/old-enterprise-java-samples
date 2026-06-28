// 06-commission-and-pricing.sc — Extract financial calculation logic
// Identifies commission rates, pricing logic, settlement calculations, and financial thresholds

@main def main(cpgFile: String = "joern-workspace/bigcorp.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("FINANCIAL LOGIC EXTRACTION")
  println("=" * 80)

  // 1. Commission calculation logic
  println("\n--- COMMISSION CALCULATION ---")
  cpg.typeDecl.filter(_.isExternal == false).l.filter { td =>
    td.name.contains("Commission") || td.name.contains("Billing")
  }.foreach { td =>
    println(s"\n  Class: ${td.fullName}")

    // Extract all members (rates, constants)
    td.member.l.foreach { m =>
      println(s"    Member: ${m.typeFullName} ${m.name}")
    }

    td.method.l.filterNot(_.name.startsWith("<")).foreach { m =>
      println(s"\n    Method: ${m.name}")

      // Find all numeric literals (rates, thresholds)
      val numerics = m.ast.isLiteral.l.filter(_.typeFullName != "java.lang.String").map(_.code).distinct
      if (numerics.nonEmpty) {
        println(s"      Numeric values: ${numerics.mkString(", ")}")
      }

      // Find all string comparisons (tier checks)
      val stringChecks = m.ast.isCall.l.filter(c => c.name == "equals" || c.name == "equalsIgnoreCase")
      stringChecks.foreach { c =>
        val args = c.argument.isLiteral.l.map(_.code)
        if (args.nonEmpty) {
          println(s"      Compares: ${args.mkString(", ")}")
        }
      }

      // Find multiplication/division (rate calculations)
      val mathOps = m.ast.isCall.l.filter { c =>
        c.name == "<operator>.multiplication" || c.name == "<operator>.division"
      }
      mathOps.foreach { c =>
        println(s"      Math op: ${c.code.take(100)}")
      }
    }
  }

  // 2. Pricing service logic
  println("\n\n--- PRICING SERVICE ---")
  cpg.typeDecl.filter(_.isExternal == false).l.filter { td =>
    td.name.contains("Pricing") || td.name.contains("Price")
  }.foreach { td =>
    println(s"\n  Class: ${td.fullName}")
    td.method.l.filterNot(_.name.startsWith("<")).foreach { m =>
      println(s"    Method: ${m.name}")
      // Find all numeric thresholds
      val nums = m.ast.isLiteral.l.filter(_.typeFullName != "java.lang.String").map(_.code).distinct
      if (nums.nonEmpty) {
        println(s"      Thresholds/values: ${nums.mkString(", ")}")
      }
      // String constants
      val strs = m.ast.isLiteral.l.filter(_.typeFullName == "java.lang.String").map(_.code).distinct
      if (strs.nonEmpty) {
        println(s"      String constants: ${strs.mkString(", ")}")
      }
    }
  }

  // 3. Settlement calculations
  println("\n\n--- SETTLEMENT LOGIC ---")
  cpg.typeDecl.filter(_.isExternal == false).l.filter { td =>
    td.fullName.contains("settlement") || td.fullName.contains("Settlement")
  }.foreach { td =>
    println(s"\n  Class: ${td.fullName}")
    td.method.l.filterNot(_.name.startsWith("<")).foreach { m =>
      val nums = m.ast.isLiteral.l.filter(_.typeFullName != "java.lang.String").map(_.code).distinct
      val strs = m.ast.isLiteral.l.filter(_.typeFullName == "java.lang.String").map(_.code).distinct.filter(_.length > 2)
      println(s"    ${m.name}:")
      if (nums.nonEmpty) println(s"      Numbers: ${nums.mkString(", ")}")
      if (strs.nonEmpty) println(s"      Strings: ${strs.mkString(", ")}")
    }
  }

  // 4. All multiplication operations (find rate calculations across codebase)
  println("\n\n--- ALL RATE/PERCENTAGE CALCULATIONS ---")
  cpg.call.l.filter(_.name == "<operator>.multiplication").foreach { c =>
    val literals = c.argument.isLiteral.l.map(_.code)
    if (literals.nonEmpty) {
      val values = literals.filter(v => v.contains(".") || v.contains("0"))
      if (values.nonEmpty) {
        println(s"  ${c.code.take(80)} -> values: ${values.mkString(", ")}")
        println(s"    In: ${c.method.fullName}")
      }
    }
  }
}
