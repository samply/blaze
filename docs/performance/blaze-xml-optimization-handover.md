# Blaze XML Paging Optimization Handover

Stand: 2026-06-08
Repo: /Users/axs/Projekte/blaze
Branch: codex/xml-direct-writer-wip

## Review-Branch-Zuschnitt, 2026-06-09

Die urspruengliche WIP-Serie wurde im Fork `astruebi/blaze` zusaetzlich als Branch `codex/xml-direct-writer-review` auf den damaligen aktuellen `origin/main` neu zugeschnitten. Der Review-Branch enthaelt dieselben finalen Aenderungen, aber in groesseren, logischeren Commits statt der kleinteiligen WIP-Historie:

1. `68b84d32 Optimize XML response output`
   - Erste Optimierung des XML-Response-Pfads sowie Profiling-/Benchmark-Setup unter `profiling/xml-paging`.
2. `37484fb5 Reduce generic XML writer overhead`
   - Reduziert generischen XML-Writer-Overhead durch guenstigere Felditeration, vor berechnete Tag-Fragmente, weniger Seq-Allokationen, schnelleres Escaping und optimierten FHIR-Type-Lookup.
3. `cddb5381 Specialize XML search bundle entries`
   - Fuegt den Fast Path fuer typische Search-`Bundle.entry`-Elemente hinzu.
4. `53928a04 Add direct XML writers for common complex types`
   - Fuehrt `XmlDirectWriter` ein und deckt erste haeufige komplexe Typen inklusive Period-Regressionstests ab.
5. `2f81c10b Write XML through a UTF-8 writer`
   - Fuehrt `XmlUtf8Writer` ein und leitet XML-Ausgabe ueber den UTF-8-Schreibpfad.
6. `3970432d Expand direct XML writing for common values`
   - Erweitert direkte XML-Ausgabe fuer Period-, Coding-, Identifier- und Meta-Faelle und optimiert ASCII-Pfade im UTF-8-Writer.
7. `9a54ce3c Document XML optimization handover`
   - Uebernimmt diese Handover-Datei als historischen Kontext.

Der folgende Inhalt wurde bewusst weitgehend unveraendert gelassen. Er beschreibt den urspruenglichen Optimierungsthread mit WIP-Commits, lokalen Benchmark-Kontexten, verworfenen Experimenten und Zwischenstaenden.

## Aktueller Kurzstand, 2026-06-08

- 8080 läuft aktuell mit `blaze:xml-direct-meta-jdk` auf Container `blaze`, Volume `blaze_blaze-data`.
- 8081 läuft weiterhin als alter Vergleichs-Blaze `blaze-old` mit `blaze:baseline-jdk-profile`, Volume `blaze_old-data`.
- Letzter gesicherter Commit vor dem aktuellen Meta-Schritt:
  - `2c6abeedb WIP write Identifier XML directly`
- Aktueller zu sichernder Schritt:
  - `XmlDirectWriter.writeMeta` plus Dispatch für `:fhir/Meta` in `writing_context.clj`.
  - Direkter Meta-Pfad für einfache `versionId`, `lastUpdated`, `source`, `profile`, `security`, `tag`; Fallback bei `id`/`extension` oder nicht direkt schreibbaren Kindern.
  - Test: `write-xml-meta-direct-test`.
- Verification für Meta:
  - `make -C modules/fhir-structure fmt`: grün.
  - `make -C modules/fhir-structure lint`: grün.
  - `make -C modules/fhir-structure test`: grün (`220 tests, 4551 assertions`).
  - `make -C modules/fhir-structure test-coverage`: grün (`ALL FILES 95,15% Forms`, `94,59% Lines`).
  - `make -C modules/fhir-structure clean`, `prep`, `make uberjar`: grün.
  - `javap`: `writeIdentifier` und `writeMeta` im Bytecode vorhanden.
- Warme lokale Benchmarks mit `_count=5000`, 8080 gleiche DB:
  - Consent XML meist `104-114 ms`, JSON meist `115-133 ms`: XML hier schneller.
  - Encounter XML meist `27-29 ms`, JSON meist `23-24 ms`: JSON noch knapp schneller.
  - Condition XML meist `16-18 ms`, JSON meist `16-17 ms`: nahe an JSON, teils gleichauf.
  - Patient XML meist `20-21 ms`, JSON grob `16-24 ms`: nahe, abhängig vom Lauf.
  - Observation XML meist `32-34 ms`, JSON meist `23-29 ms`: JSON weiterhin schneller.
- Interpretation:
  - Meta ist ein allgemeiner Writer-Hebel, weil `meta` auf vielen Ressourcen häufig vorkommt.
  - Gewinn gegenüber Identifier-only: Patient/Condition/Encounter/Observation sichtbar besser; Consent stabil/schnell.
  - Ziel "XML überall schneller als JSON" ist noch nicht erreicht, aber Consent ist mittlerweile klar der erste Ressourcentyp, bei dem XML lokal schneller als JSON läuft.
- Danach probierter, verworfener Quantity-Versuch:
  - Idee: `XmlDirectWriter.writeQuantity` für `Observation.valueQuantity`, mit direktem Decimal/String/Code/Uri-Schreiben.
  - Tests/Checks waren grün, Coverage blieb über 95%.
  - Breiter typbasierter Dispatch für `:fhir/Quantity` war klar zu teuer: Encounter/Condition/Patient wurden schlechter.
  - Entschärfte Variante nur für polymorphe `Quantity` war auch nicht überzeugend: Observation höchstens neutral/leicht besser, andere Ressourcen weiterhin zu oft schlechter oder zu instabil.
  - Ergebnis: komplett verworfen, nicht committet. 8080 wurde wieder auf `blaze:xml-direct-meta-jdk` zurückgesetzt.

## Ziel

FHIR XML Paging/Downloads in Blaze schneller machen, idealerweise schneller als JSON, weil fhircrackR aktuell XML von Blaze lädt. Lokal dient curl gegen Blaze als Proxy-Benchmark; fhircrackR kann später separat geprüft werden.

## Laufende Blaze-Instanzen

- Optimierter Blaze: http://localhost:8080/fhir
  - Container: blaze
  - Image-Tag aktuell zuletzt getestet: blaze:xml-coding-values-jdk
  - Volume: blaze_blaze-data
  - Ports: 8080:8080, 8082:8081
  - JAVA_TOOL_OPTIONS=-Xmx4g
  - Stand: Branch `codex/xml-direct-writer-wip`; lokale WIP-Commits plus aktueller noch zu committender Coding-Primitive-Kandidat
- Alter Vergleichs-Blaze: http://localhost:8081/fhir
  - Container: blaze-old
  - Image: blaze:baseline-jdk-profile
  - Volume: blaze_old-data

Beide sind mit demselben Testdatensatz beladen:
- Patient: 29021
- Encounter: 58271
- Observation: 19187
- Condition: 46217
- Consent: 19887

## Wichtige lokale Dateien

- Benchmark-Skript, untracked: profiling/xml-paging/curl-single-page.sh
- Laufzeitdaten, untracked: profiling/xml-paging/runtime/
- Handover-Datei: /private/tmp/blaze-xml-optimization-handover.md

Untracked bewusst nicht committen:
- .calva/
- axs_testdata/
- blazectl-1.0.0-darwin-arm64.tar.gz
- profiling/xml-paging/curl-single-page.sh
- profiling/xml-paging/runtime/

## Commit-Stand

Aktueller lokaler WIP-Commit vor dem jetzigen Kandidaten:
- 8b46cb488 WIP write Period dateTime XML values manually

Vorherige relevante WIP-Commits in dieser Optimierungsserie:
- f9d4e89dc WIP escape XML directly in UTF-8 writer
- 5c92ced8f WIP write XML directly as UTF-8
- 3adc225f9 WIP cover Period XML with missing end
- 0e41c80e2 WIP write Period dateTime XML values directly
- 83618c85a WIP add direct XML writers for common complex types
- 204a472bd WIP specialize XML search bundle entries
- 20fdb1f1a WIP optimize XML type lookup
- 7ea970e1e WIP optimize XML escaping
- fc6c13c3f WIP avoid XML seq allocation
- eea29c54f WIP optimize XML repeated field writing
- d7ab9a63b WIP precompute XML field tag fragments
- 2bf1987b7 WIP specialize XML resource start tag
- d2c5c0f04 WIP reduce XML field iteration overhead
- f258c703c WIP profile XML paging hot spots
- 4d2f33762 WIP optimize XML paging output
- upstream 39a83f7e6 Update jackson to v2.22.0

Späterer finaler PR soll wahrscheinlich ein sauberer einzelner Commit auf Issue-Branch werden; lokale WIP-Commits sind nur Arbeitsstände. Benchmarking-Kram nicht ins Repo übernehmen.

## Build-Falle bei Java-Änderungen

Wichtig: Bei Änderungen in `modules/fhir-structure/java` kann `make uberjar` sonst stale Klassen aus `modules/fhir-structure/target/classes` paketieren. Das ist einmal passiert: Der Jar enthielt noch eine entfernte `writeReference`-Methode und frühe Messungen liefen gegen alten Bytecode.

Pflichtablauf für Java-Änderungen:

1. `make -C modules/fhir-structure clean`
2. `make -C modules/fhir-structure prep`
3. `make uberjar`
4. `javap -classpath target/blaze-1.7.0-standalone.jar -private blaze.fhir.spec.type.XmlDirectWriter`

Jar-Kontrolle für den aktuellen Kandidaten:
- Erwartet: `writeStringValueField`, `writeStringField(Uri)`, `writeStringField(String)`, `writeStringField(Code)`, `writeBooleanField(Boolean)`.
- Erwartet nicht mehr: `writeReference`.

## Bisher erfolgreiche Optimierungen

- XML wird direkter geschrieben, weniger clojure.data.xml/emit-Overhead.
- Java-Helfer XmlUtil.writeEscaped(Writer, String) für schnelleres Escaping.
- Precomputed XML start/end/value-start tag strings in XmlPropertyHandler.
- Verbesserte Schleifen über Felder und wiederholte Werte.
- Spezialfall für Resource-Start-Tag mit FHIR Namespace.
- Polymorphe XML-Tags vorbereitet.
- Direkter fhir type lookup:
  - helper fhir-type nutzt Base/FHIR_TYPE_KEY über IPersistentMap.valAt.
  - vermeidet Keyword-Aufruf (:fhir/type value) im XML-Hotpath.
- Spezialisierter Bundle.entry XML Search-Entry Writer:
  - greift für typische Paging/Search-Entries mit fullUrl/resource/search und ohne id/extension/modifierExtension/link/request/response.
  - schreibt fullUrl, resource wrapper und search.mode/search.score direkt.
  - fällt bei komplexeren Entries oder Search mit id/extensions auf den generischen Writer zurück.

## Verifikation für aktuellen Commit

Für 20fdb1f1a:
- make -C modules/fhir-structure fmt: ok
- make -C modules/fhir-structure lint: ok
- make -C modules/fhir-structure test: 212 tests, 4516 assertions, 0 failures
- make -C modules/fhir-structure test-coverage: ALL FILES 95.29% Forms, 94.51% Lines

Für Bundle.entry Fast Path:
- make -C modules/fhir-structure fmt: ok
- make -C modules/fhir-structure lint: ok
- make -C modules/fhir-structure test: 213 tests, 4519 assertions, 0 failures
- make -C modules/fhir-structure test-coverage: ALL FILES 95.35% Forms, 94.73% Lines

## Benchmark-Lage

Baseline alt war grob sehr langsam:
- Encounter _count=1000 XML ca. 130-150 ms, JSON ca. 7-15 ms in frühen Single-Page-Messungen.
- Paging komplett war ebenfalls deutlich langsamer.

Aktueller optimierter Stand ist viel näher dran:
- Encounter _count=1000 warm oft ca. 11-14 ms, JSON ca. 7-9 ms in warmen Läufen.
- Encounter _count=5000 warm oft ca. 43-50 ms, JSON ca. 25-35 ms je nach Warmup.
- Observation _count=1000 warm ca. 12-15 ms, JSON oft ca. 7-10 ms.
- Observation _count=5000 warm ca. 45-52 ms, JSON ca. 25-30 ms.
- Consent bleibt besonders XML-lastig: _count=5000 ca. 219-229 ms, JSON ca. 109-119 ms.

Der Abstand ist kleiner, aber XML ist bei großen Seiten weiterhin klar hinter JSON.

Nach Bundle.entry Fast Path gab es den ersten klar positiven invasiveren Schritt:
- Encounter _count=1000 warm oft ca. 9.5-10.7 ms nach Warmup, JSON ca. 7.0-7.3 ms.
- Encounter _count=5000 warm stabil ca. 39-41 ms, JSON ca. 25-26 ms.
- Observation _count=1000 warm ca. 10.8-12.8 ms, JSON ca. 7.1-7.6 ms.
- Observation _count=5000 warm ca. 42-44 ms, JSON ca. 25-32 ms.
- Condition _count=1000 ca. 7.7-9.0 ms, JSON ca. 6.9-7.2 ms; _count=5000 ca. 26-29 ms, JSON ca. 19-22 ms.
- Consent bleibt sehr xml-lastig: _count=1000 ca. 51-53 ms vs JSON ca. 31-38 ms; _count=5000 ca. 205-244 ms vs JSON ca. 116-128 ms.

Interpretation:
- Fast Path behalten; er verbessert Search-Bundle-Seiten messbar, aber XML ist weiterhin nicht generell schneller als JSON.
- Das Ziel "in jedem Fall schneller als JSON" ist noch nicht erreicht.

## Aktuelles Profilbild

JFR nach 20fdb1f1a, Datei: /tmp/xmltype.jfr

Hot methods:
- blaze.fhir.XmlUtil.writeEscaped(Writer, String): ca. 8%
- blaze.fhir.spec.type.Base.valAt(Object): ca. 8%
- java.io.BufferedWriter.write(String, int, int): ca. 5%
- blaze.fhir.spec.type.AbstractElement.valAt(Object, Object): ca. 4%
- clojure.lang.Util.dohasheq / equiv: relevant
- Primitive.valueAsString and DateTime formatting: relevant
- write_xml_field_1! / write_xml_fields!: still visible

Allocations werden inzwischen stark durch DB/Resource-read-Pfad dominiert, nicht nur XML:
- resource_as_of/new_entry
- CopyOnWriteArrayList.iterator
- HashMap$KeySet.iterator
- iter_pool.State
- OffsetDateTime.ofInstant
- kleinere XML/date/string allocations

Interpretation:
- Kleine generische XML-Writer-Optimierungen sind weitgehend ausgeschöpft.
- Restkosten kommen aus Escaping/Writer, generischen FHIR-Feldzugriffen, DateTime-Formatierung und größerer XML-Payload.
- Nach Bundle.entry Fast Path sollte neu profiliert werden, weil Entry-Overhead reduziert wurde und sich Hotspots verschoben haben können.

## Verworfene Experimente

Nicht committen, da Benchmark nicht besser oder schlechter:

1. Custom Java Utf8Writer/direct OutputStream UTF-8
   - Naiver Austausch war langsamer.
   - Wenn erneut, dann als kompletter Byte-XML-Writer ohne viele Zwischen-Strings, nicht nur Writer ersetzen.

2. XML-safe primitive skip in Clojure
   - Mixed/worse.

3. java.util.List size/get loop statt aktuellem Pfad
   - Mixed/worse.

4. Primitive.writeXmlValue(Writer) für DateTime/Instant/Time
   - Tests grün, Benchmarks mixed/worse.

5. KeywordLookupSite cache pro XmlPropertyHandler
   - Tests grün nach object-array fix, Benchmarks mixed/worse.

6. Größerer Versuch: Java Base/Complex Felditeration statt StructureDefinition-Scan
   - Idee: Für Java-FHIR-Typen über ihre vorhandene geordnete Iterator-Feldliste laufen und XmlPropertyHandler per key map suchen.
   - Tests grün, keine Reflection nach Type-Hint-Fix.
   - Benchmark nicht besser, Observation/large pages schlechter.
   - Wieder entfernt, nicht committed.

7. Bundle-Root XML Search-Bundle Fast Path
   - Idee: Bundle mit id/type/total/link/entry direkt schreiben und Entry-Fast-Path wiederverwenden.
   - Tests grün: 213 tests, 4520 assertions, 0 failures; fmt/lint grün.
   - Benchmark nach Warmup höchstens neutral:
     - Encounter 1000 ca. 10-12 ms, 5000 ca. 39-41 ms.
     - Observation 1000 ca. 12-14 ms, 5000 ca. 43-46 ms.
   - Zusatzcode war relativ groß und nicht klar schneller; wieder entfernt.
   - 8080 wurde danach wieder mit dem committeten Stand 204a472bd gebaut und gestartet.

8. Coding XML Fast Path in Clojure
   - Idee: `Coding` als häufigen Java-Complex-Type direkt über Accessors schreiben, ohne generischen `valAt`-Feldscan.
   - Tests grün: 214 tests, 4520 assertions, 0 failures; fmt/lint grün.
   - Benchmark klar schlechter bei Observation:
     - Observation 1000 warm ca. 12-14 ms, nicht besser.
     - Observation 5000 warm ca. 50-56 ms statt ca. 42-46 ms.
   - Vermutung: zusätzlicher Spezial-Dispatch/Codegröße/JIT-Verhalten schlägt die eingesparten Feldzugriffe.
   - Wieder entfernt; 8080 danach wieder mit dem committeten Stand 204a472bd bauen/starten.

9. BufferedWriter Buffer 65536 -> 8192
   - Idee: JFR zeigte `BufferedWriter.<init>` als Allocation-Site; kleinerer Buffer reduziert pro Response Allocation.
   - Tests grün: 213 tests, 4519 assertions, 0 failures; fmt/lint grün.
   - Benchmark:
     - Encounter warm teils neutral/minimal besser, aber nicht stabil genug.
     - Observation 5000 schlechter: ca. 48-52 ms statt ca. 42-46 ms.
   - Wieder entfernt; 64k Buffer bleibt sinnvoll für große XML-Seiten.

10. `write-xml-str` ohne `(str ...)` für vorhandene Strings
   - Experiment:
     - `write-xml-str` änderte `(XmlUtil/writeEscaped writer (str s))` zu
       `(XmlUtil/writeEscaped writer (if (string? s) s (str s)))`.
     - Verdacht: JFR zeigte `XmlUtil.writeEscaped` und String/StringBuilder-Kosten; vielleicht unnötige Coercion vermeiden.
   - Tests:
     - `make -C modules/fhir-structure fmt`: grün.
     - `make -C modules/fhir-structure lint`: grün.
     - `make -C modules/fhir-structure test`: 213 tests, 4519 assertions, 0 failures.
   - Benchmark auf 8080 mit bestehender DB:
     - Encounter 1000 warm: ca. 10-13 ms XML vs ca. 7-8 ms JSON.
     - Encounter 5000 warm: ca. 40-42 ms XML vs ca. 24-30 ms JSON.
     - Observation 1000 warm: ca. 12-17 ms XML vs ca. 8-17 ms JSON.
     - Observation 5000 warm: ca. 54-58 ms XML vs ca. 29-35 ms JSON.
   - Ergebnis:
     - Kein stabiler Gewinn; Observation 5000 deutlich schlechter als der letzte gute Stand.
     - Änderung wieder entfernt.
     - 8080 neu gebaut und neu gestartet auf dem guten Bundle.entry-Stand (`204a472bd`).

## Aktueller gehaltener Kandidat nach `204a472bd`

11. Java `XmlDirectWriter` für häufige Complex Types
   - Neue Datei:
     - `modules/fhir-structure/java/blaze/fhir/spec/type/XmlDirectWriter.java`
   - Unterstützt aktuell:
     - `Coding`
     - `CodeableConcept`
     - `Period`
   - Die schnellen Writer schreiben nur einfache Werte ohne `id`/`extension` an Elementen und ohne Extensions an Primitive-Kindern.
   - Sobald so ein Sonderfall erkannt wird, geben sie `false` zurück, bevor etwas geschrieben wurde; der Clojure-Writer fällt dann auf den generischen Pfad zurück.
   - Integration:
     - `write-xml-field-1!` nutzt den Property-Handler-Typ für direkte Felder, damit nicht bei jedem Complex Value mehrere `instance?`-Checks laufen.
     - `write-value!` nutzt `instance?` nur für polymorphe/getaggte Werte mit vorhandenem XML-Tag.
   - Tests:
     - Roundtrip für direkte Felder: `Patient.meta.security` (`Coding`) und `HumanName.period` (`Period`).
     - Roundtrip für polymorphe Felder: `Observation.code` (`CodeableConcept`) und `Observation.effectivePeriod`.
     - Fallback-Tests mit Extensions prüfen, dass Extensions erhalten bleiben.
     - Direkte Unit-Tests auf `XmlDirectWriter/writeCodeableConcept` und `writePeriod` prüfen XML-Ausgabe und fallback-before-write.
   - Verification:
     - `make -C modules/fhir-structure fmt`: grün.
     - `make -C modules/fhir-structure lint`: grün.
     - `make -C modules/fhir-structure test`: grün (`217 tests, 4535 assertions`).
     - `make -C modules/fhir-structure test-coverage`: grün (`ALL FILES 95,39% Forms`, `94,82% Lines`).
   - Benchmarks:
     - CodeableConcept/Coding brachte bei Encounter/Observation/Condition stabil Gewinn gegenüber dem Bundle.entry-only Stand.
     - Period brachte Consent sichtbar nach vorne, aber nur mit typbasiertem Dispatch ohne breite `instance?`-Checks in `write-xml-field-1!`.
     - Breiter Period-`instance?`-Dispatch hatte Observation verschlechtert und wurde deshalb verworfen.
     - Aktueller Kandidat bleibt bei lokalen Tests weiterhin langsamer als JSON, reduziert aber einen messbaren Teil der XML-Lücke.
   - JFR auf dem Java-Fastpath-Kandidaten:
     - `XmlUtil.writeEscaped` und `BufferedWriter.write` dominieren jetzt deutlich.
     - Interpretation: Der nächste große Hebel liegt wahrscheinlich weniger in weiteren kleinen Clojure-Dispatch-Spezialisierungen und stärker in weniger Writer-Calls/einem kohärenteren XML-Byte-Writer oder generierten XML-Writer-Pfaden.
   - Frischer A/B-Vergleich gegen vorherigen Commit `204a472bd`, gleiche 8080-DB, Median der warmen Läufe 2-7:
     - Encounter `_count=1000`: 19,5 ms -> 10,9 ms, ca. 44% schneller.
     - Encounter `_count=5000`: 47,9 ms -> 39,9 ms, ca. 17% schneller.
     - Observation `_count=1000`: 12,2 ms -> 13,0 ms, ca. 6% langsamer/neutral im Rauschen.
     - Observation `_count=5000`: 54,2 ms -> 47,9 ms, ca. 12% schneller.
     - Consent `_count=1000`: 52,4 ms -> 40,2 ms, ca. 23% schneller.
     - Consent `_count=5000`: 205,5 ms -> 186,8 ms, ca. 9% schneller.
   - Fazit dieses Commits:
     - Bringt realen Zusatzgewinn, aber nicht mehr den riesigen Sprung des Bundle.entry-Fastpaths.
     - Effekt ist typenabhängig: stark bei Consent/Encounter 1000, moderat bei großen Pages, Observation 1000 neutral bis minimal schlechter.

## Neuer Stand nach `8b46cb488`

12. Java `XmlDirectWriter`: Period DateTime manuell schreiben
   - Commit: `8b46cb488 WIP write Period dateTime XML values manually`.
   - Ersetzt `DateTime.valueAsString()`/`DateTimeFormatter` im schnellen Period-Pfad durch manuelles Schreiben für den üblichen Jahresbereich `0000..9999`; außerhalb wird sauber auf den generischen Writer zurückgefallen.
   - Nach der stale-class-Korrektur neu sauber gebaut und gemessen.
   - Clean Period-only Benchmark:
     - Consent `_count=5000`: XML warm ca. `126-141 ms`, JSON ca. `118-138 ms`.
     - Observation `_count=5000`: XML warm ca. `40-46 ms`, JSON ca. `26-32 ms`.
     - Encounter `_count=5000`: XML warm ca. `33-36 ms`, JSON ca. `24-27 ms`.
   - Interpretation:
     - Consent ist damit viel näher an JSON als in älteren Messungen.
     - `DateTimeFormatter` ist im Profil fast weg; danach dominieren Escaping/Writer/String-Pfad.

13. Aktueller noch zu committender Kandidat: Coding/CodeableConcept primitive Werte direkt schreiben
   - Dateien:
     - `modules/fhir-structure/java/blaze/fhir/spec/type/XmlDirectWriter.java`
     - `modules/fhir-structure/test/blaze/fhir/spec_test.clj`
   - Idee:
     - In `writeCoding` nicht mehr `Primitive.valueAsString()` nutzen, sondern `Uri.value()`, `String.value()`, `Code.value()` und `Boolean.value()` direkt.
     - Auch `CodeableConcept.text` direkt schreiben.
     - Semantik beachten: Primitive-Objekt vorhanden aber Wert `nil` muss weiterhin `<tag/>` ergeben; primitives Objekt `nil` wird nicht geschrieben.
   - Test ergänzt:
     - `write-xml-codeable-concept-direct-test` prüft jetzt zusätzlich `Coding.userSelected`.
   - Verification vor Commit:
     - `make -C modules/fhir-structure fmt`: grün.
     - `make -C modules/fhir-structure lint`: grün.
     - `make -C modules/fhir-structure test`: grün (`218 tests, 4543 assertions`).
     - `make -C modules/fhir-structure test-coverage`: grün (`ALL FILES 95,39% Forms`, `94,81% Lines`).
     - `make -C modules/fhir-structure clean`: grün.
     - `make -C modules/fhir-structure prep`: grün.
     - `make uberjar`: grün.
     - `javap`: neue String/Boolean-Helfer im Jar, kein `writeReference`.
   - Benchmark `blaze:xml-coding-values-jdk`:
     - Consent `_count=5000`: XML warm nach Warmup ca. `110-128 ms`, JSON ca. `111-125 ms`; mehrere Läufe XML gleichauf oder leicht schneller.
     - Observation `_count=5000` seriell: XML ca. `38-41 ms` meist, JSON ca. `23-27 ms`.
     - Encounter `_count=5000`: XML ca. `34-36 ms` meist, JSON ca. `24-27 ms`.
     - Condition `_count=5000`: XML ca. `22-26 ms`, JSON ca. `18-21 ms`.
   - Interpretation:
     - Consent erreicht lokale JSON-Parität bzw. einzelne schnellere Läufe.
     - Observation/Encounter/Condition profitieren höchstens leicht und bleiben hinter JSON.
     - Der verbleibende Haupthebel ist vermutlich weniger `valueAsString()` in Coding, sondern XML-Payload/escaping/writer-call count und ggf. breitere direkte Writer-Pfade für häufige verschachtelte Typen.

14. Aktueller Kandidat: `XmlUtf8Writer` schreibt ASCII in einem Durchlauf
   - Datei:
     - `modules/fhir-structure/java/blaze/fhir/XmlUtf8Writer.java`
   - Motivation:
     - XML-only JFR auf `d5e664381` mit Consent `_count=5000` zeigte klare Writer-Dominanz:
       - `XmlUtf8Writer.writeAscii(String, int, int)`: ca. `26%`.
       - `XmlUtf8Writer.writeEscaped(String)`: ca. `25%`.
       - `XmlUtf8Writer.write(String, int, int)`: ca. `11%`.
     - Das alte Muster scannte sichere ASCII-Strings häufig einmal in `writeEscaped`/`write` und kopierte danach in `writeAscii` nochmal.
   - Änderung:
     - `writeEscaped(String)` schreibt den häufigen sicheren ASCII-Fall direkt in den internen Byte-Buffer.
     - `write(String, int, int)` und `write(char[], int, int)` schreiben ASCII ebenfalls direkt.
     - Escaping-Semantik bleibt gleich: `&`, `<`, `>`, `"` werden ersetzt; ungültige XML-Zeichen werden in `writeEscaped` zu `?`; Surrogate werden wie vorher behandelt.
   - Verification:
     - `make -C modules/fhir-structure fmt`: grün.
     - `make -C modules/fhir-structure lint`: grün.
     - `make -C modules/fhir-structure test`: grün (`218 tests, 4543 assertions`).
     - `make -C modules/fhir-structure clean`: grün.
     - `make -C modules/fhir-structure prep`: grün.
     - `make uberjar`: grün.
     - `javap`: `XmlDirectWriter` weiterhin korrekt, kein `writeReference`.
     - Docker-Image: `blaze:xml-utf8-onepass-jdk`, 8080 läuft damit auf unverändertem `blaze_blaze-data`.
     - `make -C modules/fhir-structure test-coverage`: grün (`ALL FILES 95,36% Forms`, `94,81% Lines`).
   - Benchmarks auf 8080, gleiche DB:
     - Consent `_count=5000`, 12 Läufe:
       - XML nach Warmup oft `111-125 ms`; beste warme Läufe `111-113 ms`.
       - JSON warm grob `116-139 ms`.
       - Ergebnis: XML jetzt lokal bei Consent häufig gleichauf oder schneller als JSON.
     - Observation `_count=5000`, serieller Repeat:
       - XML warm meist `33-39 ms`.
       - JSON meist `23-30 ms`.
       - Ergebnis: kein Regress; eher besser als der letzte Stand (`38-41 ms`).
     - Encounter `_count=5000`:
       - XML warm meist `35-38 ms`.
       - JSON meist `27-40 ms`.
       - Ergebnis: neutral bis leicht besser, aber nicht stabil schneller als JSON.
     - Condition `_count=5000`:
       - XML warm `21-24 ms`.
       - JSON `15-19 ms`.
       - Ergebnis: stabil, aber JSON bleibt schneller.
   - Entscheidung:
     - Behalten und lokal committen. Das ist ein allgemeiner Writer-Hebel, nicht datensatzspezifisch.

15. Aktueller Kandidat nach `538ae8b77`: schnellerer ASCII-Zweig in `writeEscaped`
   - Datei:
     - `modules/fhir-structure/java/blaze/fhir/XmlUtf8Writer.java`
   - Motivation:
     - JFR nach `538ae8b77` zeigte `writeAscii` nicht mehr als Hotspot, aber `XmlUtf8Writer.writeEscaped(String)` blieb dominant:
       - `writeEscaped`: ca. `35%`.
       - `XmlDirectWriter.writeStringValueField`: ca. `8%`.
       - `write(String, int, int)`: nur noch ca. `2%`.
   - Änderung:
     - `writeEscaped` hat jetzt zuerst einen expliziten ASCII-Fall.
     - Druckbare ASCII-Zeichen, die keine XML-Sonderzeichen sind, werden direkt in den Buffer geschrieben.
     - `&`, `<`, `>`, `"` gehen weiterhin in die Entity-Zweige.
     - ASCII-Control-Zeichen bleiben wie vorher: Tab/LF/CR erlaubt, andere werden `?`.
     - Nicht-ASCII/Surrogates bleiben semantisch unverändert.
   - Verification:
     - `make -C modules/fhir-structure test`: grün (`218 tests, 4543 assertions`).
     - `make -C modules/fhir-structure clean`: grün.
     - `make -C modules/fhir-structure prep`: grün.
     - `make uberjar`: grün.
     - Docker-Image: `blaze:xml-utf8-ascii-branch-jdk`, 8080 läuft damit auf unverändertem `blaze_blaze-data`.
     - `make -C modules/fhir-structure fmt`: grün.
     - `make -C modules/fhir-structure lint`: grün.
     - `make -C modules/fhir-structure test-coverage`: grün (`ALL FILES 95,39% Forms`, `94,81% Lines`).
   - Benchmarks auf 8080, gleiche DB:
     - Consent `_count=5000`, seriell nach Warmup:
       - XML fast durchgehend `111-121 ms`.
       - JSON meist `126-146 ms`.
       - Ergebnis: XML klar schneller als JSON in diesem Lauf.
     - Observation `_count=5000`, seriell:
       - XML stabil `33-35 ms`.
       - JSON `24-29 ms`.
       - Ergebnis: kein Regress, sehr guter XML-Wert.
     - Encounter `_count=5000`:
       - XML meist `33-35 ms`, ein Ausreißer `47 ms`.
       - JSON meist `25-32 ms`.
       - Ergebnis: neutral/leicht besser, JSON bleibt im Median schneller.
     - Condition `_count=5000`:
       - XML meist `21-23 ms`, ein Ausreißer `28 ms`.
       - JSON meist `17-19 ms`.
       - Ergebnis: stabil, JSON bleibt schneller.
   - Entscheidung:
     - Behalten und lokal committen. Es ist ein allgemeiner, kleiner Writer-Hebel und hat keine beobachtete Regression in den Zielressourcen.

12. Period DateTime ohne Zwischenstring schreiben
   - Profiling nach Commit `83618c85a`:
     - `XmlUtil.writeEscaped(Writer, String)`: ca. 28% Samples.
     - `BufferedWriter.write(String, int, int)`: ca. 24%.
     - `Primitive.valueAsString()`: ca. 5,8%.
     - `DateTimeFormatter...format`: ca. 5,5%.
   - Änderung:
     - In `XmlDirectWriter.writePeriod` werden `start`/`end` jetzt über `writeDateTimeField` geschrieben.
     - Für `LocalDateTime`/`OffsetDateTime` nutzt das direkt `DateTimeFormatter.formatTo(..., writer)`.
     - Dadurch entfällt im direkten Period-Pfad der Zwischenstring aus `DateTime.valueAsString()` und das anschließende XML-Escaping des DateTime-Strings.
     - Fallback bei `id`/`extension` bleibt unverändert, weil `writePeriod` weiterhin vorher `canWritePeriod` prüft.
   - Verification:
     - `clojure -T:build compile` in `modules/fhir-structure`: grün.
     - `make -C modules/fhir-structure fmt`: grün.
     - `make -C modules/fhir-structure lint`: grün.
     - `make -C modules/fhir-structure test`: grün (`217 tests, 4535 assertions`).
     - `make -C modules/fhir-structure test-coverage`: grün (`ALL FILES 95,36% Forms`, `94,82% Lines`).
   - Benchmarks auf 8080 mit gleicher DB, warme Läufe:
     - Consent `_count=1000`: ca. 38-52 ms, Median warm ca. 42 ms; vorher grob ca. 40 ms, also neutral bis leicht besser.
     - Consent `_count=5000`: ca. 143-178 ms, Median warm ca. 160 ms; vorher grob ca. 187 ms, also klar schneller.
     - Observation `_count=1000`: ca. 9,7-14,6 ms, keine Regression.
     - Observation `_count=5000`: ca. 43-56 ms, keine Regression gegenüber vorher grob ca. 48 ms.
     - Encounter/Condition blieben unauffällig/neutral bis leicht besser im lokalen Lauf.
   - Fazit:
     - Kleiner, messbarer Gewinn vor allem bei periodlastigem Consent.
     - Behalten und lokal committen.

13. Verworfener Versuch: direkter `dateTime`/`instant` Primitive-Fastpath
   - Motivation:
     - Nach Commit `0e41c80e2` zeigte JFR weiterhin `Primitive.valueAsString()` und `DateTimeFormatter` als relevante Kosten.
     - Idee: `:fhir/dateTime` und `:fhir/instant` im generischen XML-Field-Dispatch direkt schreiben, ohne `valueAsString()` und ohne XML-Escaping des sicheren DateTime-Strings.
   - Tests zuerst:
     - Direkte Tests für `XmlDirectWriter/writeDateTime` und `writeInstant` wurden geschrieben und scheiterten erwartbar, weil Methoden fehlten.
   - Implementiert:
     - `XmlDirectWriter/writeDateTime`
     - `XmlDirectWriter/writeInstant`
     - Dispatch in `write-xml-field-1!` für `:fhir/dateTime`/`:fhir/instant`.
     - `write-value!`-Zweige für polymorphe DateTime/Instant-Werte.
   - Gefundener Fehler:
     - `Encounter` XML bei `_count=1000/5000` wurde plötzlich nur ca. 21 KB groß.
     - Logs zeigten NPE beim XML-Output.
     - Ursache: direkter `writeDateTime` behandelte `nil` nicht wie der bisherige Primitive-Pfad; `Period` mit nur `start` oder nur `end` rief den Writer mit `nil` für das fehlende Kind auf.
     - Regressionstest ergänzt: `write-xml-period-direct-test` mit "Period with only start".
   - Fix:
     - `writeDateTime`/`writeInstant` wurden für `nil` gefixt; Tests danach grün.
   - Benchmark nach Fix:
     - `Encounter` Payload wieder korrekt, aber nicht schneller; eher schlechter als der letzte gute Stand.
     - `Consent 5000` ungefähr im Bereich des letzten Commits, nicht klar besser.
     - `Observation` teils besser, aber nicht genug, um die Regression/Komplexität zu rechtfertigen.
   - Entscheidung:
     - Breiter `dateTime`/`instant`-Fastpath entfernt.
     - Regressionstest für `Period` mit nur `start` behalten.
     - 8080 neu gebaut und wieder auf den guten Stand ohne diesen Versuch zurückgesetzt.

14. Experiment: eigener UTF-8 Writer für XML Output
   - Stand danach uncommitted, 2026-06-07:
     - Neue Java-Klasse `blaze.fhir.XmlUtf8Writer`.
     - `blaze.fhir.spec/write-xml` nutzt `XmlUtf8Writer` statt `BufferedWriter(OutputStreamWriter(... UTF_8), 65536)`.
     - Writer schreibt direkt in einen byte buffer auf den `OutputStream`.
     - ASCII-Runs werden blockweise in den Buffer kopiert; Nicht-ASCII wird als UTF-8 codiert; Surrogat-Paare werden korrekt behandelt.
     - Test `xml-utf8-writer-test` in `spec_test.clj` deckt `Aä😀BC` ab.
   - TDD:
     - Test zuerst hinzugefügt, lief rot mit `ClassNotFoundException: blaze.fhir.XmlUtf8Writer`.
     - Danach Java-Klasse implementiert und `write-xml` umgestellt.
   - Korrektheit/Checks:
     - `clojure -T:build compile` grün.
     - `make -C modules/fhir-structure fmt` grün.
     - `make -C modules/fhir-structure lint` grün.
     - `make -C modules/fhir-structure test` grün, 218 Tests, 4538 Assertions.
     - `make -C modules/fhir-structure test-coverage` grün, `ALL FILES 95,39% Forms`.
   - 8080 neu gebaut mit Image `blaze:xml-utf8-writer-jdk`; DB-Volume `blaze_blaze-data` blieb erhalten.
   - Payload sanity:
     - `Encounter?_count=1000` XML: `1480534` bytes.
     - `Consent?_count=1000` XML: `8790820` bytes.
   - Benchmarks gegen 8080, Warmup jeweils kritisch:
     - Erste naive Writer-Version:
       - `Encounter 5000` warm ca. `43.9-47.4 ms`, nicht klar besser.
       - `Consent 5000` warm ca. `146.4-155.2 ms`, leicht besser als bisher grob `~160 ms`.
       - `Observation 5000` warm ca. `44.3-51.7 ms`, neutral bis leicht besser.
     - Optimierte ASCII-Run-Version:
       - `Consent 5000`: `152.1-181.2 ms`, Median etwa im Bereich `160 ms`, also neutral/leicht besser, aber mit Ausreißer.
       - `Observation 5000`: `43.6-45.9 ms`, gut/stabil.
       - `Encounter 5000` erste Runde nach Restart: `43.1-56.7 ms`; zweite warme Runde: `35.6-42.3 ms`.
       - `Encounter 1000` zweite warme Runde: `9.1-12.1 ms`, aber JSON dort `~7-8 ms`.
   - Interpretation:
     - Writer ist allgemein und korrekt.
     - Effekt ist kleiner als die direkten Complex-Type-Fastpaths, aber warm offenbar positiv oder neutral.
     - Nicht überbewerten: Messungen bleiben JIT/Warmup-sensibel, besonders direkt nach Neustart.
     - Nächster sinnvoller Schritt: JFR nach Writer, um zu sehen, ob `OutputStreamWriter`/`BufferedWriter` aus den Hot Methods verschwunden ist und ob nun `XmlUtf8Writer.writeAscii`/`writeEscaped`/FHIR field dispatch dominieren.

15. Experiment: direktes XML-Escaping im `XmlUtf8Writer`
   - JFR nach dem Writer zeigte bei XML-only `Consent?_count=5000`:
     - `XmlUtil.writeEscaped(Writer, String)` 18,64%
     - `XmlUtf8Writer.writeAscii(String, int, int)` 18,05%
     - `XmlUtf8Writer.write(String, int, int)` 10,95%
     - `Primitive.valueAsString()` 7,10%
     - `DateTimeFormatter...format` weiterhin sichtbar.
   - Interpretation:
     - Der alte `XmlUtil.writeEscaped` scannt den String und ruft dann `writer.write(...)`.
     - `XmlUtf8Writer.write(...)` scannt dieselben ASCII-Abschnitte erneut.
   - Änderung:
     - `XmlUtf8Writer.writeEscaped(String)` ergänzt.
     - `XmlUtil.writeEscaped` delegiert bei `writer instanceof XmlUtf8Writer` direkt dorthin.
     - Test `xml-utf8-writer-test` erweitert um Escaping inklusive `&`, `<`, `>`, `"`, Nicht-ASCII, Surrogat-Paar und invalidem XML-Control-Char.
   - TDD:
     - Test zuerst rot mit `No matching method writeEscaped`.
     - Danach Implementierung und `XmlUtil`-Brücke.
   - Checks:
     - `clojure -T:build compile` grün.
     - `make -C modules/fhir-structure fmt` grün.
     - `make -C modules/fhir-structure lint` grün.
     - `make -C modules/fhir-structure test` grün, 218 Tests, 4539 Assertions.
     - `make -C modules/fhir-structure test-coverage` grün, `ALL FILES 95,39% Forms`.
   - 8080 neu gebaut mit `blaze:xml-utf8-writer-jdk`, DB-Volume blieb erhalten.
   - Benchmarks nach Escape-Fastpath:
     - `Consent 5000`: warm `142.6-160.0 ms`, meist ca. `145-156 ms`; vorher im Writer-Stand eher `~160-170 ms`, nach Period-Fastpath grob `~160 ms`.
     - `Encounter 5000`: warm `32.9-34.7 ms`, bisher meist `35-42 ms`.
     - `Observation 5000`: warm `41.0-47.8 ms`, neutral bis leicht besser.
   - Interpretation:
     - Das ist ein allgemeiner Gewinn im XML-Outputpfad, weil alle XML-Escapes über `XmlUtil.writeEscaped` laufen.
     - Keine sichtbare Regression bei Encounter/Observation.
     - Nächster sinnvoller Schritt: nochmal XML-only-JFR auf diesem Stand; wahrscheinlich bleiben `Primitive.valueAsString`, DateTime formatting, `PersistentArrayMap.indexOf`, `write_xml_fields_BANG_` als nächste Kandidaten.

16. Verworfener Versuch: allgemeiner Primitive-Fastpath ohne `valueAsString`
   - Idee:
     - `XmlDirectWriter/writePrimitiveField` öffentlich machen und in `writing_context.clj` vor dem bisherigen Primitive-Pfad aufrufen.
     - Kontrollierte Werte (`boolean`, `integer`, `dateTime`, `instant`) direkt schreiben.
     - Strings/URIs/Codes zunächst breit über Java-Switch/default laufen lassen, danach auf eine engere Variante ohne String-Default reduziert.
   - TDD:
     - Test `write-xml-primitive-field-direct-test` hinzugefügt und rot gesehen.
     - Java/Clojure-Implementierung danach grün.
   - Ergebnis:
     - Modultests grün.
     - Breite Variante war nicht stabil schneller; `Consent 5000` wurde eher schlechter.
     - Engere Variante war ebenfalls nicht überzeugend:
       - Seriell warm `Encounter 5000` etwa `35-38 ms`, okay aber nicht besser als Escape-Stand.
       - Seriell warm `Consent 5000` etwa `159-166 ms`, schlechter als die guten Escape-Stand-Läufe (`~145-156 ms`).
   - Entscheidung:
     - Versuch vollständig entfernt, nicht committed.
     - 8080 neu gebaut und wieder auf letzten guten Stand (`5c92ced8f`, direkter Escape-Pfad) gesetzt.
   - Erkenntnis:
     - `valueAsString` breit zu vermeiden ist nicht automatisch ein Gewinn, weil ein generischer Hook/Switch alle String-lastigen Primitive trifft.
     - Der nächste Ansatz sollte konkreter sein: JFR-getrieben pro heißem FHIR-Typ/Feld oder generierter Writer, nicht ein universeller Primitive-Interceptor.

## Wahrscheinliche nächste invasive Varianten

Variante A: Spezialisierte XML-Serialisierung in Java-FHIR-Typen
- Analog zur existierenden JSON-Spezialisierung.
- Primitive/Complex/Resource-Typen bekommen serializeXmlField/serializeXmlValue oder ähnliches.
- Ziel: weniger generischer Clojure-Dispatch, weniger valAt-Scans, weniger tag lookup.
- Risiko: viele Java-Typen betroffen oder Generator/Codegen nötig.
- Vermutlich stärkster Hebel.

Variante B: Generierter XML-Writer pro FHIR-Typ
- Aus StructureDefinitions einmal Writer-Funktionen/classes generieren.
- Pro Typ direkte Feldzugriffe/Handler, keine generische Feldliste zur Laufzeit.
- Könnte in Clojure oder Java laufen; Java wahrscheinlich schneller, Clojure einfacher.
- Risiko: größerer Architekturumbau.

Variante C: Vollständiger UTF-8 Byte XML Writer
- Erster Schritt ist jetzt als `XmlUtf8Writer` umgesetzt.
- Weitere Ausbaustufe wäre konsequenter: Tags und häufige Attribute als UTF-8 bytes precomputen, Escaping direkt bytes schreiben und ggf. Writer-API im XML-Pfad schrittweise ersetzen.
- Nur sinnvoll, wenn JFR nach dem Writer noch Writer/Escaping als klare Hotspots zeigt.

Variante D: Date/Time XML formatting spezialisieren
- DateTime/Instant/Time valueAsString erzeugt aktuell Strings.
- Eigenes writeXmlValue kann theoretisch direkt schreiben.
- Frühere kleine Version war nicht gut genug, aber im Rahmen von A oder C wieder sinnvoll.

Variante E: Payload/Compression
- gzip hilft vor allem bei Netzwerk/IO, lokal weniger bei CPU-Pfad.
- Könnte abhängig von Latenz/Bandbreite/Größe aktiviert werden, ist aber ein anderer Hebel als reine Server-CPU.

## Nächster sinnvoller Schritt

Nach Commit des aktuellen Java-Complex-Type-Fastpaths neu profilieren und dann mit den invasiveren Varianten weitergehen, jeweils als Experiment mit schneller Rückfallmöglichkeit:

1. Optional JFR auf aktuellem Bundle.entry Stand:
   docker exec blaze jcmd 1 JFR.start name=bundleentry settings=profile filename=/tmp/bundleentry.jfr duration=45s
   profiling/xml-paging/curl-single-page.sh Encounter '1000' 80
   docker cp blaze:/tmp/bundleentry.jfr /tmp/bundleentry.jfr
   jfr view hot-methods /tmp/bundleentry.jfr
   jfr view allocation-by-site /tmp/bundleentry.jfr
2. Weitere kleine Typ-Fastpaths nur noch dann wählen, wenn JFR/Benchmark einen klaren Hotspot zeigt. `Reference`/`Identifier` wären Kandidaten, aber der aktuelle JFR deutet eher auf Writer/Escaping hin.
3. Test zuerst über bestehende XML Roundtrip/Writer-Tests plus gezielten Test falls öffentliches Verhalten sichtbar wird.
4. Benchmark gegen 8080-Datensatz mit Encounter/Observation/Condition/Consent bei _count=1000 und 5000.
5. Nur behalten, wenn stabil besser; sonst entfernen wie die anderen Experimente.

## Nützliche Befehle

Health:
curl -fsS http://localhost:8080/health

Benchmark:
profiling/xml-paging/curl-single-page.sh Encounter '1000 5000' 7
profiling/xml-paging/curl-single-page.sh Observation '1000 5000' 7
profiling/xml-paging/curl-single-page.sh Condition '1000 5000' 5
profiling/xml-paging/curl-single-page.sh Consent '1000 5000' 5

Build/restart 8080 preserving DB:
make uberjar
docker build -f profiling/xml-paging/Dockerfile.jdk-profile -t blaze:xml-direct-context-jdk .
docker rm -f blaze
docker run -d --name blaze -p 8080:8080 -p 8082:8081 -e BASE_URL=http://localhost:8080 -e JAVA_TOOL_OPTIONS=-Xmx4g -v blaze_blaze-data:/app/data blaze:xml-direct-context-jdk

JFR:
docker exec blaze jcmd 1 JFR.start name=xml settings=profile filename=/tmp/xml.jfr duration=45s
profiling/xml-paging/curl-single-page.sh Encounter '1000' 80
docker cp blaze:/tmp/xml.jfr /tmp/xml.jfr
jfr view hot-methods /tmp/xml.jfr
jfr view allocation-by-site /tmp/xml.jfr

Verification:
make -C modules/fhir-structure fmt
make -C modules/fhir-structure lint
make -C modules/fhir-structure test
make -C modules/fhir-structure test-coverage

## Update 2026-06-07: Reference verworfen, Period-DateTime direkt geschrieben

### Verworfener Versuch: `Reference` Fastpath

- Idee:
  - Simple `Reference` direkt in `XmlDirectWriter` schreiben, falls `id`, `extension` und `identifier` fehlen.
  - Ziel war weniger Clojure-Dispatch bei häufigen Referenzfeldern.
- Ergebnis:
  - Tests waren grün, aber Benchmarks waren nicht überzeugend.
  - `Encounter` blieb etwa im bekannten Bereich, `Consent` wurde eher schlechter (`5000` warm grob 160-176 ms statt zuvor eher 145-160 ms).
- Entscheidung:
  - Vollständig entfernt, nicht committed.
  - Erkenntnis: ein weiterer komplexer Typ-Fastpath lohnt nur, wenn JFR ihn klar zeigt; zusätzliche Dispatch-Prüfungen können bei vielen Feldern selbst kosten.

### Behaltene Änderung: direkter Period-DateTime-Writer

- JFR nach `5c92ced8f` zeigte weiterhin:
  - `XmlUtf8Writer.writeAscii` ~21%
  - `XmlUtf8Writer.writeEscaped` ~14%
  - `DateTimeFormatterBuilder$CompositePrinterParser.format` ~9%
  - `Primitive.valueAsString` ~7%
  - Allocation: `DateTimeFormatter.formatTo` ~20%, `AbstractStringBuilder` ~20%, `Arrays.copyOfRange` ~19%
- Änderung:
  - `XmlDirectWriter.writePeriod` nutzt für `LocalDateTime` und `OffsetDateTime` keinen `DateTimeFormatter.formatTo` mehr.
  - Jahr/Monat/Tag/Uhrzeit/Fraction/Offset werden direkt in den `Writer` geschrieben.
  - Zusätzlicher Test für Fractional Seconds + UTC: `2019-01-01T00:00:00.12Z`.
  - Zusätzlicher Fallback-Test für programmgesteuerte Jahre außerhalb des direkten Bereichs, z.B. `+10000-01-01T00:00:00`.
- Benchmarks mit `blaze:xml-period-datetime-jdk`, bestehendem Datenvolume:
  - `Consent?_count=5000`: warm bis ca. 144-153 ms; vorher guter Bereich eher ca. 145-160 ms, Vergleichslauf letzter Stand nach Warmup ca. 157-160 ms.
  - `Observation?_count=5000`: warm ca. 38-40 ms; vorher eher ca. 41-48 ms.
  - `Encounter?_count=5000`: seriell warm ca. 35-38 ms; keine klare Verbesserung, aber auch keine belastbare Regression.
  - Kleine Pages `_count=1000`: XML bleibt knapp hinter JSON, keine auffällige Regression.
- Verification:
  - `make -C modules/fhir-structure fmt` OK
  - `make -C modules/fhir-structure lint` OK
  - `make -C modules/fhir-structure test` OK: 218 tests, 4543 assertions
  - `make -C modules/fhir-structure test-coverage` OK: ALL FILES 95.36% forms
- Aktueller 8080:
  - Läuft mit Image `blaze:xml-period-datetime-jdk`.
  - 8081 alter Baseline-Blaze bleibt unverändert.

## Update 2026-06-08: Type-Cache verworfen, Identifier direkt geschrieben

### Verworfener Versuch: bekannten FHIR-Typ in `write-value!` weiterreichen

- Idee:
  - Bei polymorphen XML-Feldern wird `:fhir/type` zur Tag-Bestimmung gelesen und direkt danach in `write-value!` nochmal für Handler-Dispatch.
  - Versuch: `write-value!` um eine interne Arity mit bereits bekanntem Typ erweitern.
- Ergebnis:
  - Erster Versuch hatte einen Bug für polymorphe Primitive (`DateTime`), wurde gefixt.
  - Korrekte Variante war benchmarkseitig kein Gewinn; Consent blieb grob auf bisherigem Niveau, andere Ressourcen teils neutral bis leicht schlechter.
- Entscheidung:
  - Vollständig entfernt, nicht behalten.

### Behaltene Änderung: direkter `Identifier`-Writer

- Änderung:
  - `XmlDirectWriter.writeIdentifier` schreibt einfache Identifier direkt.
  - Erlaubt sind Identifier ohne `id`, ohne `extension`, ohne `assigner`; `use`, `type`, `system`, `value`, `period` dürfen vorhanden sein, solange die enthaltenen Werte ebenfalls direkt schreibbar sind.
  - Hook in `writing_context.clj` für Felder vom Typ `:fhir/Identifier` und direkte Werte vom Java-Typ `Identifier`.
  - Neuer Test `write-xml-identifier-direct-test` prüft einfache Ausgabe und Fallback vor dem Schreiben bei Extension.
- Verification:
  - Rotlauf vor Implementierung: erwarteter Compile-Fehler, `XmlDirectWriter/writeIdentifier` fehlt.
  - `make -C modules/fhir-structure test` OK: 219 tests, 4547 assertions.
  - `make -C modules/fhir-structure fmt` OK.
  - `make -C modules/fhir-structure lint` OK.
  - `make -C modules/fhir-structure test-coverage` OK: ALL FILES 95.17% forms.
  - `javap` zeigt `writeIdentifier(java.io.Writer, java.lang.String, blaze.fhir.spec.type.Identifier)`.
- Benchmark mit `blaze:xml-direct-identifier-jdk`, bestehendem 8080-Datenvolume, warm `_count=5000`:
  - Consent XML ca. 105-110 ms, JSON meist ca. 116-120 ms.
  - Encounter XML ca. 29-31 ms, JSON meist ca. 24-30 ms.
  - Condition XML ca. 19-20 ms, JSON meist ca. 16-17 ms.
  - Patient XML ca. 22-23 ms, JSON meist ca. 17-19 ms.
  - Observation XML ca. 34-36 ms, JSON meist ca. 24-25 ms; etwa neutral gegenüber vorher.
- Aktueller 8080:
  - Läuft mit Image `blaze:xml-direct-identifier-jdk`.
  - 8081 alter Baseline-Blaze bleibt unverändert.

## Update 2026-06-08: Meta direkt geschrieben, Quantity und Bundle-Entry-Shortcut verworfen

### Behaltene Änderung: direkter `Meta`-Writer

- Aktueller Commit: `1fcaa9011 WIP write Meta XML directly`.
- Änderung:
  - `XmlDirectWriter.writeMeta` schreibt einfache `Meta` direkt.
  - Direkter Pfad für `versionId`, `lastUpdated`, `source`, `profile`, `security`, `tag`, solange keine `id`/`extension` und nur direkt schreibbare Primitive/Codings vorhanden sind.
  - Hook in `writing_context.clj` für `:fhir/Meta` und direkte Werte vom Java-Typ `Meta`.
- Verification:
  - `make -C modules/fhir-structure fmt` OK.
  - `make -C modules/fhir-structure lint` OK.
  - `make -C modules/fhir-structure test` OK: 220 tests, 4551 assertions.
  - `make -C modules/fhir-structure test-coverage` OK: ALL FILES 95.15% forms.
  - Clean/prep/uberjar OK; `javap` zeigte `writeIdentifier` und `writeMeta`.
- Benchmark mit `blaze:xml-direct-meta-jdk`, bestehendem 8080-Datenvolume, warm `_count=5000`:
  - Consent XML ca. 104-114 ms, JSON ca. 115-133 ms; XML ist hier schneller.
  - Encounter XML ca. 27-29 ms, JSON ca. 23-24 ms.
  - Condition XML ca. 16-18 ms, JSON ca. 16-17 ms.
  - Patient XML ca. 20-21 ms, JSON ca. 16-24 ms.
  - Observation XML ca. 32-34 ms, JSON ca. 23-29 ms.

### Verworfener Versuch: direkter `Quantity`-Writer

- Idee:
  - `Observation.valueQuantity` bzw. Quantity-Felder direkt schreiben.
- Ergebnis:
  - Breiter type-basierter Hook war zu teuer und regressierte Encounter/Condition/Patient.
  - Enger polymorpher Hook war für Observation neutral bis leicht besser, aber andere Ressourcen waren unstabil bzw. teils schlechter.
- Entscheidung:
  - Vollständig entfernt, nicht committed.

### Verworfener Versuch: kompakter `Bundle.entry`-Shortcut

- Profil nach Meta zeigte Hotspots in `write-xml-fields!`, `Base.valAt`, Hash/Equiv und `write-search-bundle-entry!`.
- Idee:
  - Häufige Such-Bundle-Entries enthalten nur `:fhir/type`, `:fullUrl`, `:resource`, `:search`.
  - Für diese kompakte Form sollten sechs negative `.valAt`-Lookups auf `:id`, `:extension`, `:modifierExtension`, `:link`, `:request`, `:response` übersprungen werden.
- Test/Build:
  - Zusätzlicher Schutztest für Entry mit `request` wurde temporär ergänzt.
  - `make -C modules/fhir-structure test` OK mit dem Versuch.
  - Testimage `blaze:xml-entry-compact-jdk` gebaut.
- Benchmark warm `_count=5000`, zweiter Lauf, Median ohne Run 1:
  - Consent XML 108.5 ms vs JSON 129.4 ms.
  - Condition XML 18.5 ms vs JSON 18.6 ms.
  - Encounter XML 29.2 ms vs JSON 26.8 ms.
  - Observation XML 34.3 ms vs JSON 26.4 ms.
  - Patient XML 22.2 ms vs JSON 19.3 ms.
- Entscheidung:
  - Nicht behalten. Consent/Condition sehen gut aus, aber der Gesamtgewinn ist nicht robust genug und bringt Encounter/Observation/Patient nicht näher an JSON.
  - Änderungen vollständig entfernt; getrackter Arbeitsbaum wieder sauber.

### Aktueller Zustand

- 8080 läuft wieder mit Image `blaze:xml-direct-meta-jdk` und Volume `blaze_blaze-data`.
- 8081 läuft weiter mit altem Baseline-Image `blaze:baseline-jdk-profile` und Volume `blaze_old-data`.
- Getrackter Arbeitsbaum ist sauber; nur lokale/untracked Benchmark-/Hilfsdateien sind vorhanden.

## Update 2026-06-08: Generischer Resource-Sparse-Writer verworfen

### Kontext

- Es wurde explizit festgelegt, dass kein Patch akzeptabel ist, der auf eine bestimmte FHIR-Version, konkrete Resource-Felder oder Profile hardcodiert ist.
- Ein kurz angesetzter Encounter-spezifischer Spike wurde deshalb sofort wieder entfernt, bevor er getestet oder behalten wurde.

### Verworfener versionsneutraler Versuch

- Idee:
  - Für Resource-Handler zusätzlich aus den vorhandenen `element-definitions` eine `key -> XmlPropertyHandler + FHIR-order`-Tabelle erzeugen.
  - Dünn besetzte Resources sollten nur ihre tatsächlich vorhandenen Keys einsammeln, nach StructureDefinition-Reihenfolge sortieren und schreiben.
  - Bei unbekannten vorhandenen Keys sollte der alte generische Writer fallbacken.
- Eigenschaften:
  - Keine hardcodierten Resource-Namen.
  - Keine hardcodierten Feldlisten.
  - Feldreihenfolge und Handler blieben aus denselben StructureDefinitions abgeleitet wie bisher.
- Verification während des Spikes:
  - `make -C modules/fhir-structure fmt` OK.
  - `make -C modules/fhir-structure lint` OK.
  - `make -C modules/fhir-structure test` OK: 220 tests, 4551 assertions.
- Benchmark mit `blaze:xml-sparse-resource-jdk`, bestehendem 8080-Datenvolume, warm `_count=5000`, zweiter Lauf, Median ohne Run 1:
  - Consent XML 113.5 ms vs JSON 149.1 ms.
  - Encounter XML 28.9 ms vs JSON 24.9 ms.
  - Condition XML 19.0 ms vs JSON 17.2 ms.
  - Observation XML 34.3 ms vs JSON 29.7 ms.
  - Patient XML 24.7 ms vs JSON 18.9 ms.
- Entscheidung:
  - Nicht behalten. Gegen `xml-direct-meta` ist der Clojure-Sparse-Pfad nicht robust schneller und verschlechtert Patient/Condition.
  - Vermutung: Array-Erzeugung, Map-Seq und Sortierung fressen die eingesparten `.valAt`-Lookups auf.
  - Änderungen vollständig entfernt; getrackter Arbeitsbaum wieder sauber.

### Aktueller Zustand nach diesem Versuch

- 8080 läuft wieder mit Image `blaze:xml-direct-meta-jdk`.
- 8081 läuft weiter mit altem Baseline-Image `blaze:baseline-jdk-profile`.
