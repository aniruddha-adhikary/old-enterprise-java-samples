// 00-calibrate.sc — Auto-discover project conventions so the other scripts need no hardcoding.
// Emits a PROJECT PROFILE: root package, modules, integration vocabulary, status constants,
// rule/financial class hints, and issue-tracker marker patterns.
//
// Run FIRST. Use its output to ground Stage 2 extraction and to sanity-check the other reports.
// Run: joern --script 00-calibrate.sc --param cpgFile=<path-to>.cpg

@main def main(cpgFile: String = "workspace.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("PROJECT PROFILE (auto-calibrated)")
  println("=" * 80)

  val internal = cpg.typeDecl.filterNot(_.isExternal).filter(_.fullName.contains(".")).l

  // --- Root package: longest common dotted prefix across all internal types ---
  val pkgs = internal.map(_.fullName.split("\\.").dropRight(1).mkString(".")).filter(_.nonEmpty).distinct
  val rootPackage = {
    if (pkgs.isEmpty) ""
    else {
      val split = pkgs.map(_.split("\\.").toList)
      val minLen = split.map(_.size).min
      var prefix = List.empty[String]
      var i = 0
      var stop = false
      while (i < minLen && !stop) {
        val seg = split.head(i)
        if (split.forall(_(i) == seg)) { prefix = prefix :+ seg; i += 1 } else stop = true
      }
      prefix.mkString(".")
    }
  }
  println(s"\n--- ROOT PACKAGE ---\n  $rootPackage")

  // --- Modules: the next package segment under the root ---
  val rootDepth = if (rootPackage.isEmpty) 0 else rootPackage.split("\\.").length
  val modules = internal
    .map(_.fullName.split("\\."))
    .filter(_.length > rootDepth + 1)
    .map(_(rootDepth))
    .groupBy(identity).view.mapValues(_.size).toList
    .sortBy(-_._2)
  println("\n--- MODULES (package segment under root, by type count) ---")
  modules.foreach { case (m, n) => println(s"  $m  ($n types)") }
  println(s"\n  // For cross-module analysis, modules = List(${modules.map("\"" + _._1 + "\"").mkString(", ")})")

  // --- Rule abstraction: the dominant interface that many small classes implement ---
  println("\n--- CANDIDATE RULE/STRATEGY INTERFACES (implemented by 3+ types) ---")
  internal.flatMap(_.inheritsFromTypeFullName).groupBy(identity).view.mapValues(_.size).toList
    .filter(_._2 >= 3).sortBy(-_._2)
    .foreach { case (iface, n) => println(s"  $iface  ($n implementers)") }

  // --- Integration vocabulary: queue/topic/endpoint-like literals (no hardcoded brand) ---
  println("\n--- INTEGRATION LITERALS (dotted UPPER tokens — likely queue/topic names) ---")
  cpg.literal.l
    .map(_.code.replaceAll("\"", ""))
    .filter(s => s.matches("[A-Z][A-Z0-9]*(\\.[A-Z0-9]+){1,}"))
    .distinct.sorted.foreach(s => println(s"  $s"))

  println("\n--- INTEGRATION API USAGE (by external method namespace) ---")
  val integrationNs = List("jms", "javax.mail", "jcraft", "jsch", "sftp", "soap", "wsdl",
                           "httpurlconnection", "jdbc", "socket", "kafka", "amqp")
  cpg.call.l.map(_.methodFullName.toLowerCase)
    .filter(fn => integrationNs.exists(fn.contains))
    .groupBy(fn => integrationNs.find(fn.contains).get)
    .view.mapValues(_.size).toList.sortBy(-_._2)
    .foreach { case (k, n) => println(s"  $k: $n calls") }

  // --- Status constants: derive the real status vocabulary, not a fixed list ---
  println("\n--- STATUS CONSTANT FIELDS (STATUS_*, *_STATUS_*, STATE_*) ---")
  internal.foreach { td =>
    val s = td.member.l.filter(m => m.name.matches("(STATUS|STATE)_.*") || m.name.matches(".*_STATUS_.*"))
    if (s.nonEmpty) println(s"  ${td.name}: ${s.map(_.name).mkString(", ")}")
  }
  println("\n--- UPPER_SNAKE STRING LITERALS (candidate status/enum values) ---")
  cpg.literal.l.filter(_.typeFullName == "java.lang.String")
    .map(_.code.replaceAll("\"", ""))
    .filter(s => s.matches("[A-Z][A-Z_]{2,}") && !s.contains(" "))
    .groupBy(identity).view.mapValues(_.size).toList.sortBy(-_._2).take(40)
    .foreach { case (v, n) => println(s"  $v  ($n)") }

  // --- Issue-tracker / regulatory markers: discover the prefixes instead of assuming JIRA- ---
  println("\n--- ISSUE/TICKET MARKER PREFIXES (TOKEN-1234 style, discovered) ---")
  val markerRe = "[A-Z]{2,}-\\d+(?:-\\d+)?".r
  cpg.literal.l.flatMap(lit => markerRe.findAllIn(lit.code.replaceAll("\"", "")))
    .map(_.replaceAll("\\d+", "N"))
    .groupBy(identity).view.mapValues(_.size).toList.sortBy(-_._2)
    .foreach { case (pat, n) => println(s"  $pat  ($n occurrences)") }

  // --- DOMAIN DISCOVERY (no hardcoded domain) -------------------------------------------------
  // The skill must adapt to whatever this project IS. Instead of grepping for finance words,
  // derive the domain vocabulary and the candidate "critical operations" from the code itself,
  // then feed them as parameters to the advanced scripts (13-16).
  val techSuffix = Set("DAO","DTO","TO","Impl","Factory","Service","Listener","Servlet","Manager",
    "Helper","Util","Utils","Controller","Handler","Processor","Command","Rule","Test","Exception",
    "Builder","Adapter","Client","Endpoint","Job","Scheduler","Filter","Bootstrap","Engine","Main",
    "Config","Logger","Producer","Consumer","Assembler","Delegate","Repository","Mapper","Parser",
    "Object","Bean","Entity","Model","Info","Data","Base","Abstract","Default","Generic","Thread")
  def camelTokens(s: String) = s.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[._$]").filter(_.nonEmpty)

  println("\n--- DOMAIN NOUNS (from class names, tech suffixes stripped) ---")
  internal.flatMap(td => camelTokens(td.name).filterNot(techSuffix.contains))
    .filter(t => t.length > 2 && t.matches("[A-Za-z]+")).map(_.toLowerCase)
    .groupBy(identity).view.mapValues(_.size).toList.sortBy(-_._2).take(20)
    .foreach { case (w, n) => println(f"  $w%-20s $n") }

  println("\n--- DOMAIN VERBS (leading verb of business method names) ---")
  val genericVerbs = Set("get","set","is","to","from","of","as","new","equals","hash","hashcode",
    "tostring","main","run","init","destroy","close","open","read","write","add","remove","size","clone")
  cpg.method.filterNot(_.isExternal).name.filterNot(_.startsWith("<")).l
    .map(n => camelTokens(n).headOption.getOrElse("").toLowerCase)
    .filter(v => v.length > 2 && !genericVerbs.contains(v))
    .groupBy(identity).view.mapValues(_.size).toList.sortBy(-_._2).take(20)
    .foreach { case (v, n) => println(f"  $v%-20s $n") }

  // Candidate CRITICAL OPERATIONS: internal methods that DECIDE or COMPUTE — return a
  // number/boolean/enum and branch — regardless of domain. These are what the advanced scripts
  // should treat as high-stakes sinks. Pass the dominant verbs to 13/16 via --param computeVerbs.
  println("\n--- CANDIDATE CRITICAL OPERATIONS (decision/compute methods, by branch count) ---")
  cpg.method.filterNot(_.isExternal).filterNot(_.name.startsWith("<"))
    .filterNot(m => Set("get","set","is").exists(m.name.startsWith)).l
    .filter(m => m.methodReturn.typeFullName.matches("double|float|int|long|boolean|java.lang.String") ||
                 m.methodReturn.typeFullName.matches(".*(" + (if (rootPackage.isEmpty) "MODEL" else rootPackage.replace(".","\\.")) + ").*"))
    .map(m => (m.fullName, m.controlStructure.size))
    .filter(_._2 >= 2).sortBy(-_._2).take(20)
    .foreach { case (fn, n) => println(f"  branches=$n%2d  $fn") }

  println("\n--- SUMMARY ---")
  println(s"  Internal types: ${internal.size}")
  println(s"  Methods: ${cpg.method.size}   Calls: ${cpg.call.size}   Literals: ${cpg.literal.size}")
  println(s"  Root package: '$rootPackage'   Modules: ${modules.size}")
  println("\n  NEXT:")
  println("  - pass --param rootPackage=\"" + rootPackage + "\" to scripts 07 and 18")
  println("  - read DOMAIN NOUNS/VERBS above, name the domain, and pass the domain compute verbs")
  println("    (e.g. the top business verbs) to scripts 13 and 16 via --param computeVerbs=\"v1,v2,...\"")
  println("  - see references/grounding-on-current-project.md for the adaptive workflow")
}
