// 01-explore-cpg.sc — Basic CPG exploration: what's in the graph?
// Run: joern --script joern-workspace/scripts/01-explore-cpg.sc --param cpgFile=joern-workspace/bigcorp.cpg

@main def main(cpgFile: String = "joern-workspace/bigcorp.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("CPG EXPLORATION REPORT")
  println("=" * 80)

  // 1. Namespace / package overview
  println("\n--- PACKAGES ---")
  cpg.namespace.name.l.sorted.foreach(p => println(s"  $p"))

  // 2. All type declarations (classes/interfaces)
  println("\n--- TYPE DECLARATIONS ---")
  cpg.typeDecl.filter(_.isExternal == false).name.l.sorted.foreach(t => println(s"  $t"))

  // 3. Method count per type
  println("\n--- METHODS PER TYPE ---")
  cpg.typeDecl.filter(_.isExternal == false).l.sortBy(_.fullName).foreach { td =>
    val methods = td.method.name.l.filterNot(_.startsWith("<"))
    if (methods.nonEmpty) {
      println(s"  ${td.fullName} (${methods.size} methods)")
      methods.sorted.foreach(m => println(s"    - $m"))
    }
  }

  // 4. Total counts
  println("\n--- SUMMARY ---")
  println(s"  Types: ${cpg.typeDecl.filter(_.isExternal == false).size}")
  println(s"  Methods: ${cpg.method.size}")
  println(s"  Calls: ${cpg.call.size}")
  println(s"  Literals: ${cpg.literal.size}")
  println(s"  Files: ${cpg.file.name.l.size}")
}
