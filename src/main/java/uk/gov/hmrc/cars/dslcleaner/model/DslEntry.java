package uk.gov.hmrc.cars.dslcleaner.model;

/**
 * Represents a single [then] entry parsed from a DSL file.
 *
 * @param key       the plain-English phrase between [then] and =, trimmed
 * @param value     the Java expression after =, up to and including the semicolon
 * @param startLine the 0-based line index of the [then] line in the source file
 * @param endLine   the 0-based line index of the line containing the closing semicolon
 */
public record DslEntry(String key, String value, int startLine, int endLine) {}