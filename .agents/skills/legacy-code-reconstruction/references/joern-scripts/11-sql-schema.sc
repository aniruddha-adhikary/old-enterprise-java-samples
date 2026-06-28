// 11-sql-schema.sc — Extract complete database schema from DDL and DML statements
// Reconstructs table definitions from CREATE TABLE, INSERT, SELECT patterns

@main def main(cpgFile: String = "workspace.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("DATABASE SCHEMA EXTRACTION")
  println("=" * 80)

  // 1. CREATE TABLE statements
  println("\n--- CREATE TABLE STATEMENTS ---")
  cpg.literal.l.filter { lit =>
    lit.code.toUpperCase.contains("CREATE TABLE")
  }.sortBy(_.code).foreach { lit =>
    println(s"\n  ${lit.code}")
    println(s"    In: ${lit.method.fullName}")
  }

  // 2. INSERT statements (reveal table structure and seed data)
  println("\n\n--- INSERT STATEMENTS ---")
  cpg.literal.l.filter { lit =>
    lit.code.toUpperCase.contains("INSERT INTO")
  }.sortBy(_.code).foreach { lit =>
    println(s"\n  ${lit.code.take(200)}")
    println(s"    In: ${lit.method.fullName}")
  }

  // 3. SELECT statements (reveal column usage)
  println("\n\n--- SELECT STATEMENTS ---")
  cpg.literal.l.filter { lit =>
    lit.code.toUpperCase.contains("SELECT") && !lit.code.toUpperCase.contains("CREATE")
  }.sortBy(_.code).foreach { lit =>
    println(s"\n  ${lit.code.take(200)}")
    println(s"    In: ${lit.method.fullName}")
  }

  // 4. UPDATE statements
  println("\n\n--- UPDATE STATEMENTS ---")
  cpg.literal.l.filter { lit =>
    lit.code.toUpperCase.contains("UPDATE ") && lit.code.toUpperCase.contains("SET ")
  }.sortBy(_.code).foreach { lit =>
    println(s"\n  ${lit.code.take(200)}")
    println(s"    In: ${lit.method.fullName}")
  }

  // 5. DELETE statements
  println("\n\n--- DELETE STATEMENTS ---")
  cpg.literal.l.filter { lit =>
    lit.code.toUpperCase.contains("DELETE FROM")
  }.sortBy(_.code).foreach { lit =>
    println(s"\n  ${lit.code.take(200)}")
    println(s"    In: ${lit.method.fullName}")
  }

  // 6. Table names referenced across codebase
  println("\n\n--- TABLE REFERENCES (from SQL) ---")
  val tableNames = cpg.literal.l.filter { lit =>
    val upper = lit.code.toUpperCase
    upper.contains("SELECT") || upper.contains("INSERT") || upper.contains("UPDATE") ||
    upper.contains("DELETE") || upper.contains("CREATE TABLE")
  }.flatMap { lit =>
    val upper = lit.code.toUpperCase
    // Extract table names after FROM, INTO, UPDATE, TABLE
    val patterns = List(
      "FROM\\s+([A-Z_]+)".r,
      "INTO\\s+([A-Z_]+)".r,
      "UPDATE\\s+([A-Z_]+)".r,
      "TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([A-Z_]+)".r,
      "JOIN\\s+([A-Z_]+)".r
    )
    patterns.flatMap(_.findAllMatchIn(upper).map(_.group(1)))
  }.distinct.sorted

  println("  Detected tables:")
  tableNames.foreach(t => println(s"    - $t"))
}
