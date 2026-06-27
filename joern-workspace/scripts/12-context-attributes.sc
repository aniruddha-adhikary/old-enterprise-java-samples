// 12-context-attributes.sc — Track RuleContext attributes: who sets them, who reads them
// Reveals unused features and incomplete integrations

@main def main(cpgFile: String = "joern-workspace/bigcorp.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("RULECONTEXT ATTRIBUTE FLOW ANALYSIS")
  println("=" * 80)

  // 1. Find all setAttribute calls
  println("\n--- ATTRIBUTES SET (written) ---")
  val setterCalls = cpg.call.l.filter { c =>
    c.name == "setAttribute" || c.name == "stashAttribute"
  }
  setterCalls.foreach { c =>
    val args = c.argument.isLiteral.l.map(_.code)
    val keyArg = args.headOption.getOrElse("?")
    println(s"  Set: $keyArg")
    println(s"    Code: ${c.code.take(120)}")
    println(s"    In: ${c.method.fullName}")
  }

  // 2. Find all getAttribute calls
  println("\n\n--- ATTRIBUTES READ (consumed) ---")
  val getterCalls = cpg.call.l.filter { c =>
    c.name == "getAttribute"
  }
  getterCalls.foreach { c =>
    val args = c.argument.isLiteral.l.map(_.code)
    val keyArg = args.headOption.getOrElse("?")
    println(s"  Get: $keyArg")
    println(s"    Code: ${c.code.take(120)}")
    println(s"    In: ${c.method.fullName}")
  }

  // 3. Compare sets vs gets to find orphaned attributes
  println("\n\n--- ATTRIBUTE FLOW SUMMARY ---")
  val setKeys = setterCalls.flatMap(_.argument.isLiteral.l.map(_.code)).distinct.sorted
  val getKeys = getterCalls.flatMap(_.argument.isLiteral.l.map(_.code)).distinct.sorted

  val setOnly = setKeys.filterNot(getKeys.contains)
  val getOnly = getKeys.filterNot(setKeys.contains)
  val both = setKeys.filter(getKeys.contains)

  println("\n  Attributes SET but never READ (potential dead features):")
  setOnly.foreach(k => println(s"    - $k"))

  println("\n  Attributes READ but never SET (potential bugs):")
  getOnly.foreach(k => println(s"    - $k"))

  println("\n  Attributes both SET and READ (active data flow):")
  both.foreach(k => println(s"    - $k"))

  // 4. Find setRejected calls (rule rejection points)
  println("\n\n--- REJECTION POINTS ---")
  cpg.call.l.filter(_.name == "setRejected").foreach { c =>
    val args = c.argument.isLiteral.l.map(_.code)
    println(s"  Rejection: ${args.mkString(", ")}")
    println(s"    In: ${c.method.fullName}")
  }
}
