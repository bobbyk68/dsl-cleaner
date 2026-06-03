package uk.gov.hmrc.cars.dslcleaner.parser;

import uk.gov.hmrc.cars.dslcleaner.model.DslEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses [then] entries from Drools DSL files.
 *
 * Each entry starts on a line beginning with [then] and ends at the first
 * line containing a semicolon. The key is the plain-English phrase between
 * [then] and the first = sign, which may appear on the same line or the next.
 *
 * Both formats are handled:
 *
 * Single-line:
 *   [then] Emit BR092 validation error for consignor party name =
 *       insert(emitter.emit(drools, BR092, of($cons, CONSIGNOR_PARTY_NAME)));
 *
 * Two-line (key wraps before =):
 *   [then] Emit BR150 validation error for declaration header
 *       customs office of presentation =
 *       insert(emitter.emit(drools, BR150, of($dec, CUSTOMS_OFFICE_PRESENTATION)));
 */
public class DslParser {

    /**
     * Parses all [then] entries from the given DSL file.
     */
    public List<DslEntry> parse(Path dslFile) throws IOException {
        List<String> lines = Files.readAllLines(dslFile);
        return parseLines(lines);
    }

    /**
     * Core parsing logic operating on raw lines.
     * Exposed with package-friendly visibility for unit testing.
     */
    public List<DslEntry> parseLines(List<String> lines) {
        List<DslEntry> entries = new ArrayList<>();
        int i = 0;

        while (i < lines.size()) {
            String trimmed = lines.get(i).stripLeading();

            if (trimmed.startsWith("[then]")) {
                int startLine = i;
                String afterThen = trimmed.substring(6).stripLeading();
                int equalsPos = afterThen.indexOf('=');

                String key;
                StringBuilder valueBuilder = new StringBuilder();

                if (equalsPos >= 0) {
                    // Key and = are on the same line as [then]
                    key = afterThen.substring(0, equalsPos).strip();
                    valueBuilder.append(afterThen.substring(equalsPos + 1).stripLeading());
                } else {
                    // Key continues on this line; = is on a subsequent line
                    key = afterThen.strip();
                    i++;

                    while (i < lines.size()) {
                        String nextLine = lines.get(i);
                        int eqPos = nextLine.indexOf('=');

                        if (eqPos >= 0) {
                            // Any text before = on this line is a key continuation
                            String beforeEq = nextLine.substring(0, eqPos).strip();
                            if (!beforeEq.isEmpty()) {
                                key = key + " " + beforeEq;
                            }
                            valueBuilder.append(nextLine.substring(eqPos + 1).stripLeading());
                            break;
                        }
                        i++;
                    }
                }

                // Consume value lines until we hit a semicolon
                while (!valueBuilder.toString().contains(";") && i + 1 < lines.size()) {
                    i++;
                    valueBuilder.append("\n").append(lines.get(i));
                }

                entries.add(new DslEntry(key.strip(), valueBuilder.toString().strip(), startLine, i));
            }
            i++;
        }

        return entries;
    }
}