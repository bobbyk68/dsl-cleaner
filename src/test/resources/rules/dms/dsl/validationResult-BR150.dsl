// Declaration - Header

[then] Emit BR150 validation error for declaration header total gross mass =
    insert(emitter.emit(drools,BR150, of($dec, TOTAL_GROSS_MASS)));

[then] Emit BR150 validation error for declaration header reference number =
    insert(emitter.emit(drools,BR150, of($dec, HEADER_REFERENCE_NUMBER)));

// Two-line entry - key wraps before the equals sign
[then] Emit BR150 validation error for declaration header
    customs office of presentation =
    insert(emitter.emit(drools,BR150, of($dec, CUSTOMS_OFFICE_PRESENTATION)));

// This entry is NOT in the dslr - should be removed
[then] Emit BR150 validation error for declaration header transport document number =
    insert(emitter.emit(drools,BR150, of($dec, TRANSPORT_DOCUMENT_NUMBER)));
