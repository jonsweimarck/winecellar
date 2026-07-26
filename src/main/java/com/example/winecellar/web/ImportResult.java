package com.example.winecellar.web;

/**
 * Resultatet av ett faktiskt commit-steg (WINE-25) - hur många viner
 * som sparades som nya, hur många som bara fick antalet ökat (fullständig
 * eller partiell dubblett + "öka antal"-strategin), och hur många som
 * hoppades över (valt bort, eller inte kunde tolkas alls).
 */
record ImportResult(int imported, int increased, int skipped) {
}
