// Consignment Shipment - Consignor

[then] Emit BR092 validation error for consignment shipment consignor physical address street and number =
    insert(emitter.emit(drools,BR092, of($cons, CONSIGNOR_PHYSICAL_ADDRESS_STREET_NUMBER)));

[then] Emit BR092 validation error for consignment shipment consignor physical address country code =
    insert(emitter.emit(drools,BR092, of($cons, CONSIGNOR_PHYSICAL_ADDRESS_COUNTRY_CODE)));

[then] Emit BR092 validation error for consignment shipment consignor physical address city name =
    insert(emitter.emit(drools,BR092, of($cons, CONSIGNOR_PHYSICAL_ADDRESS_CITY_NAME)));

[then] Emit BR092 validation error for consignment shipment consignor physical address zip code =
    insert(emitter.emit(drools,BR092, of($cons, CONSIGNOR_PHYSICAL_ADDRESS_ZIP_CODE)));

[then] Emit BR092 validation error for consignment shipment consignor party name =
    insert(emitter.emit(drools,BR092, of($cons, CONSIGNOR_PARTY_NAME)));

// Consignment Shipment - Consignee

[then] Emit BR092 validation error for consignment shipment consignee physical address street and number =
    insert(emitter.emit(drools,BR092, of($cons, CONSIGNEE_PHYSICAL_ADDRESS_STREET_NUMBER)));

[then] Emit BR092 validation error for consignment shipment consignee party identification number =
    insert(emitter.emit(drools,BR092, of($cons, CONSIGNEE_PARTY_ID_NUMBER)));

[then] Emit BR092 validation error for consignment shipment consignee street and number =
    insert(emitter.emit(drools,BR092, of($cons, CONSIGNEE_PHYSICAL_ADDRESS_STREET_NUMBER)));

[then] Emit BR092 validation error for consignment shipment consignee party name =
    insert(emitter.emit(drools,BR092, of($cons, CONSIGNEE_PARTY_NAME)));

// This entry is NOT used in the dslr - should be flagged for removal
[then] Emit BR092 validation error for consignment shipment consignor physical address region =
    insert(emitter.emit(drools,BR092, of($cons, CONSIGNOR_PHYSICAL_ADDRESS_REGION)));

// This entry is also NOT used in the dslr - should be flagged for removal
[then] Emit BR092 validation error for consignment shipment consignee email address =
    insert(emitter.emit(drools,BR092, of($cons, CONSIGNEE_EMAIL_ADDRESS)));
