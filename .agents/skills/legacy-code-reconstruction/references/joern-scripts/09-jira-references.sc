// 09-jira-references.sc — Extract issue-tracker / ticket / regulatory references from the code.
// Generic: discovers ANY "TOKEN-1234" or "TOKEN-12-34" style marker prefix (JIRA-, REG-,
// ISO-, project keys, ...) instead of assuming a single tracker. Provides code<->ticket
// traceability and surfaces evolution/era markers.
//
// Optional: pass --param markerPrefixes="JIRA,REG,FOO" to restrict to known prefixes.

@main def main(cpgFile: String = "workspace.cpg", markerPrefixes: String = "") = {
  importCpg(cpgFile)

  println("=" * 80)
  println("ISSUE / TICKET / REGULATORY MARKERS")
  println("=" * 80)

  val markerRe = "[A-Z]{2,}-\\d+(?:-\\d+)*".r
  val wanted = markerPrefixes.split(",").map(_.trim).filter(_.nonEmpty).toSet
  // Common encoding/charset tokens that look like markers but aren't tickets.
  val noise = Set("UTF", "ASCII", "SHA", "MD", "RFC", "UTF8")

  // Collect every marker occurrence (token -> contexts), prefix-filtered if requested.
  case class Hit(token: String, prefix: String, context: String, method: String, file: String)
  val hits = cpg.literal.l.flatMap { lit =>
    markerRe.findAllIn(lit.code.replaceAll("\"", "")).toList.map { tok =>
      val prefix = tok.takeWhile(_ != '-')
      Hit(tok, prefix, lit.code.take(120), lit.method.fullName,
          lit.file.name.l.headOption.getOrElse("unknown"))
    }
  }.filter(h => !noise.contains(h.prefix) && (wanted.isEmpty || wanted.contains(h.prefix)))

  // 1. Summary: which marker namespaces exist and how often.
  println("\n--- MARKER PREFIXES (discovered) ---")
  hits.groupBy(_.prefix).toList.sortBy(-_._2.size).foreach { case (p, hs) =>
    println(s"  $p-*  (${hs.size} occurrences, ${hs.map(_.token).distinct.size} distinct)")
  }

  // 2. Detail by individual ticket/marker.
  println("\n\n--- REFERENCES BY MARKER ---")
  hits.groupBy(_.token).toList.sortBy(_._1).foreach { case (tok, hs) =>
    println(s"\n  $tok:")
    hs.distinct.foreach { h =>
      println(s"    Context: ${h.context}")
      println(s"    In: ${h.method}   File: ${h.file}")
    }
  }

  // 3. Evolution / era markers (free-text, not TOKEN-NNN): "wave 2", "circa 2009", "phase 3".
  println("\n\n--- EVOLUTION / ERA MARKERS ---")
  cpg.literal.l.filter { lit =>
    val s = lit.code
    s.matches("(?i).*\\b(wave|phase|era|circa|legacy|v\\d|version)\\b.*\\d+.*")
  }.map(_.code).distinct.sorted.foreach { code =>
    println(s"  ${code.take(120)}")
  }
}
