package uk.gov.hmrc.cars.dslcleaner.matcher;

/**
 * Checks whether a DSL entry key is referenced in a DSLR rule file.
 *
 * A key is considered used if it appears as a trimmed line anywhere
 * in the DSLR content — typically as a statement inside a "then" block:
 *
 *   then
 *       Emit BR092 validation error for consignor party name
 *   end
 */
public class DslrMatcher {

    /**
     * Returns true if the given key appears as a trimmed line in the DSLR content.
     *
     * @param key         the plain-English phrase from the DSL [then] entry
     * @param dslrContent the full text content of the paired DSLR file
     */
    public boolean isKeyUsed(String key, String dslrContent) {
        for (String line : dslrContent.split("\\r?\\n")) {
            if (line.strip().equals(key)) {
                return true;
            }
        }
        return false;
    }
}