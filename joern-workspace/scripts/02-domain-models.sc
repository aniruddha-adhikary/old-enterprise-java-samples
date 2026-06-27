// 02-domain-models.sc — Extract domain model structure: fields, types, relationships
// Identifies core domain entities, their attributes, and inter-entity references

@main def main(cpgFile: String = "joern-workspace/bigcorp.cpg") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("DOMAIN MODEL EXTRACTION")
  println("=" * 80)

  // 1. Identify domain model classes (in .model package or with entity-like patterns)
  val modelTypes = cpg.typeDecl.filter(_.isExternal == false).l.filter { td =>
    td.fullName.contains(".model.") ||
    td.fullName.contains(".dto.") ||
    // Also detect classes with mostly getters/setters (POJO pattern)
    {
      val methods = td.method.name.l.filterNot(_.startsWith("<"))
      val gettersSetters = methods.count(m => m.startsWith("get") || m.startsWith("set") || m.startsWith("is"))
      methods.nonEmpty && gettersSetters.toDouble / methods.size > 0.6
    }
  }

  println("\n--- DOMAIN ENTITIES (model/DTO classes and POJOs) ---")
  modelTypes.sortBy(_.fullName).foreach { td =>
    println(s"\n  CLASS: ${td.fullName}")

    // Extract fields (member variables)
    td.member.l.foreach { m =>
      println(s"    FIELD: ${m.typeFullName} ${m.name}")
    }

    // Extract getters/setters to infer properties
    val methods = td.method.l.filterNot(_.name.startsWith("<"))
    val getters = methods.filter(_.name.startsWith("get"))
    val setters = methods.filter(_.name.startsWith("set"))
    val otherMethods = methods.filterNot(m => m.name.startsWith("get") || m.name.startsWith("set") || m.name.startsWith("is"))

    if (otherMethods.nonEmpty) {
      println(s"    BUSINESS METHODS:")
      otherMethods.foreach(m => println(s"      - ${m.name}(${m.parameter.l.filterNot(_.name == "this").map(p => s"${p.typeFullName} ${p.name}").mkString(", ")}): ${m.methodReturn.typeFullName}"))
    }
  }

  // 2. Identify enums and constants that define domain vocabulary
  println("\n\n--- DOMAIN CONSTANTS AND ENUMS ---")
  cpg.typeDecl.filter(_.isExternal == false).l.foreach { td =>
    val staticFinalFields = td.member.l.filter { m =>
      m.name == m.name.toUpperCase && m.name.length > 1
    }
    if (staticFinalFields.nonEmpty) {
      println(s"\n  ${td.fullName}:")
      staticFinalFields.foreach { m =>
        println(s"    ${m.typeFullName} ${m.name}")
      }
    }
  }

  // 3. Identify status/state fields and their possible values
  println("\n\n--- STATUS/STATE LITERALS ---")
  val statusLiterals = cpg.literal.l.filter { lit =>
    val v = lit.code.replaceAll("\"", "")
    v == v.toUpperCase && v.length > 2 && v.contains("_") == false &&
    (v.matches("[A-Z]+") || v.matches("[A-Z_]+"))
  }.map(_.code).distinct.sorted
  println("  Potential status values found in code:")
  statusLiterals.foreach(v => println(s"    $v"))

  // 4. Find string literals that look like status transitions
  println("\n\n--- STRING CONSTANTS (potential business values) ---")
  val bizLiterals = cpg.literal.l.filter { lit =>
    lit.typeFullName == "java.lang.String" &&
    lit.code.length > 3 &&
    lit.code.length < 50
  }.map(_.code).distinct.sorted
  bizLiterals.foreach(v => println(s"    $v"))

  // 5. Cross-references between domain types
  println("\n\n--- INTER-ENTITY REFERENCES ---")
  modelTypes.foreach { td =>
    val refs = td.member.l.filter { m =>
      modelTypes.exists(mt => m.typeFullName.contains(mt.name))
    }
    if (refs.nonEmpty) {
      println(s"\n  ${td.name} references:")
      refs.foreach(r => println(s"    -> ${r.typeFullName} ${r.name}"))
    }
  }
}
