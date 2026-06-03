# DSL Cleaner

A command-line utility for auditing and cleaning **Drools DSL files** used in the Cleaner engine.

Over time, `[then]` entries accumulate in `validationResult-BRXXX.dsl` files that no longer have a corresponding rule in their paired `.dslr` file. This tool identifies those orphaned entries, produces a detailed report, and writes cleaned copies of any affected DSL files — leaving your originals untouched.

---

## How It Works

Each `validationResult-BRXXX.dsl` file is paired with its counterpart DSLR rule file at `dslr/BRXXX/BRXXX_rules.dslr`. The tool:

1. Parses every `[then]` entry from the DSL file, extracting the key (the plain-English phrase between `[then]` and `=`)
2. Searches the paired DSLR file for that key as a statement in a `then` block
3. Flags any entry whose key cannot be found in the DSLR
4. Prints a per-rule-set summary to the console
5. Writes a `dsl-cleaner-report.txt` to the `rules/dms` root
6. Prompts for confirmation, then writes `.cleaned` versions of any modified DSL files alongside the originals

> **Your original files are never modified.** Cleaned output is written as `validationResult-BRXXX.dsl.cleaned`.

---

## Project Structure

```
dsl-cleaner/
├── pom.xml
└── src/
    ├── main/java/uk/gov/hmrc/cars/dslcleaner/
    │   └── DslCleaner.java          # Single-class utility — no framework dependencies
    └── test/
        ├── java/uk/gov/hmrc/cars/dslcleaner/
        │   └── DslCleanerTest.java  # JUnit 5 test suite (13 tests)
        └── resources/rules/dms/
            ├── dsl/
            │   ├── validationResult-BR092.dsl   # Test data modelled on real BR092 rules
            │   └── validationResult-BR150.dsl   # Includes a two-line key entry
            └── dslr/
                ├── BR092/BR092_rules.dslr
                └── BR150/BR150_rules.dslr
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+     |
| Maven | 3.8+   |

---

## Building

```bash
# Clone the repo
git clone https://github.com/bobbyk68/dsl-cleaner.git
cd dsl-cleaner

# Compile
mvn compile

# Run all tests
mvn test

# Build the runnable jar
mvn package
```

---

## Running

```bash
java -jar target/dsl-cleaner-1.0-SNAPSHOT.jar <path-to-rules/dms>
```

**Example:**
```bash
java -jar target/dsl-cleaner-1.0-SNAPSHOT.jar \
  ~/projects/centralised-automated-rules-system/src/main/resources/rules/dms
```

---

## Example Console Output

```
======================================================================
 DSL Cleaner — Analysis Report
======================================================================

[BR092] validationResult-BR092.dsl
  Total entries : 11
  In use        : 9
  Unused        : 2
  Entries to remove:
    - Emit BR092 validation error for consignment shipment consignor physical address region
    - Emit BR092 validation error for consignment shipment consignee email address

[BR150] validationResult-BR150.dsl
  Total entries : 4
  In use        : 3
  Unused        : 1
  Entries to remove:
    - Emit BR150 validation error for declaration header transport document number

======================================================================
  Grand total entries : 15
  Total unused        : 3
======================================================================

Report written to: /path/to/rules/dms/dsl-cleaner-report.txt

Proceed with writing .cleaned files? [yes/no]:
```

Answering `yes` writes the cleaned files and prints a confirmation:

```
Written: validationResult-BR092.dsl.cleaned
Written: validationResult-BR150.dsl.cleaned

Done. 2 .cleaned file(s) written, 3 entries removed.
```

---

## DSL File Format

The tool handles both single-line and two-line `[then]` entry formats:

**Single-line** (key and `=` on the same line):
```
[then] Emit BR092 validation error for consignment shipment consignor party name =
    insert(emitter.emit(drools,BR092, of($cons, CONSIGNOR_PARTY_NAME)));
```

**Two-line** (key wraps before the `=`):
```
[then] Emit BR150 validation error for declaration header
    customs office of presentation =
    insert(emitter.emit(drools,BR150, of($dec, CUSTOMS_OFFICE_PRESENTATION)));
```

---

## Report File

A `dsl-cleaner-report.txt` is written to the `rules/dms` root after every run, regardless of whether any unused entries were found. Example:

```
DSL Cleaner Report
Generated : 2026-06-03 11:45:00
Root      : /path/to/rules/dms
======================================================================

[BR092] validationResult-BR092.dsl
  DSL file  : .../dsl/validationResult-BR092.dsl
  DSLR file : .../dslr/BR092/BR092_rules.dslr
  Total     : 11
  In use    : 9
  Unused    : 2
  Entries removed:
    - Emit BR092 validation error for consignment shipment consignor physical address region
    - Emit BR092 validation error for consignment shipment consignee email address
  Cleaned file: validationResult-BR092.dsl.cleaned
```

---

## Test Coverage

The JUnit 5 suite covers:

| # | Test | Description |
|---|------|-------------|
| 1 | Single-line entry parsing | Key and `=` on same line as `[then]` |
| 2 | Two-line entry parsing | Key wraps to next line before `=` |
| 3 | BR092 entry count | Expects 11 entries |
| 4 | BR150 entry count | Expects 4 entries |
| 5 | All keys non-blank | Guards against malformed entries |
| 6 | Key found in DSLR | Returns true for a known-used key |
| 7 | Key not found in DSLR | Returns false for an absent key |
| 8 | Key match is trimmed | Leading/trailing whitespace ignored |
| 9 | Partial key does not match | Whole line must equal the key |
| 10 | BR092 unused count | Exactly 2 unused entries identified |
| 11 | BR092 unused keys | Correct entries flagged (region + email) |
| 12 | BR150 two-liner recognised as used | `customs office of presentation` correctly retained |
| 13 | Cleaned file correctness | Removed entries absent; used entries retained; original untouched |

---

## Notes

- The tool matches keys **exactly** (trimmed). Internal whitespace is not normalised — if Drools parses the files successfully, the whitespace is consistent.
- If a `validationResult-BRXXX.dsl` has no matching `dslr/BRXXX/` folder, it is skipped with a warning and included in the report.
- This tool is a **PoC** built for local validation before applying changes in the CARS DMS project environment.

---


