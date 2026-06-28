// 12-context-attributes.sc — Track RuleContext attributes: who sets them, who reads them
// Reveals unused features and incomplete integrations

@main def main(cpgFile: String = "workspace.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("RULECONTEXT ATTRIBUTE FLOW ANALYSIS")
  println("=" * 80)

  // The attribute KEY is the first real argument (argumentIndex 1): index 0 is the
  // receiver, index 2+ are VALUES. Collecting every literal arg (the old approach)
  // wrongly folded values like setAttribute("priority","HIGH") -> "HIGH" into the key
  // set, manufacturing false "dead attributes". Restrict to the key position.
  def keyOf(c: io.shiftleft.codepropertygraph.generated.nodes.Call): Option[String] =
    c.argument.argumentIndex(1).isLiteral.code.headOption

  // 1. Find all attribute writes. Add put/setProperty so Map/Properties-style
  //    context objects are covered too, not just a setAttribute() API.
  println("\n--- ATTRIBUTES SET (written) ---")
  val setterCalls = cpg.call.l.filter { c =>
    c.name == "setAttribute" || c.name == "stashAttribute" || c.name == "put" || c.name == "setProperty"
  }
  var dynamicSetters = 0
  setterCalls.foreach { c =>
    val keyArg = keyOf(c).getOrElse { dynamicSetters += 1; "(dynamic/non-literal key)" }
    println(s"  Set: $keyArg")
    println(s"    Code: ${c.code.take(120)}")
    println(s"    In: ${c.method.fullName}")
  }

  // 2. Find all attribute reads
  println("\n\n--- ATTRIBUTES READ (consumed) ---")
  val getterCalls = cpg.call.l.filter { c =>
    c.name == "getAttribute" || c.name == "get" || c.name == "getProperty"
  }
  var dynamicGetters = 0
  getterCalls.foreach { c =>
    val keyArg = keyOf(c).getOrElse { dynamicGetters += 1; "(dynamic/non-literal key)" }
    println(s"  Get: $keyArg")
    println(s"    Code: ${c.code.take(120)}")
    println(s"    In: ${c.method.fullName}")
  }

  // 3. Compare sets vs gets to find orphaned attributes (literal keys only)
  println("\n\n--- ATTRIBUTE FLOW SUMMARY ---")
  println(s"  CAVEAT: $dynamicSetters write(s) and $dynamicGetters read(s) use dynamic or")
  println( "  constant (static-final) keys not visible as literals — e.g. a bulk copy loop.")
  println( "  Treat SET-but-never-READ below as candidates to confirm in source, not proof.")
  val setKeys = setterCalls.flatMap(keyOf).distinct.sorted
  val getKeys = getterCalls.flatMap(keyOf).distinct.sorted

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
