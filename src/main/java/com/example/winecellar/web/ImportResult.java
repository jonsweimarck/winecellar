package com.example.winecellar.web;

/**
 * Resultatet av ett faktiskt commit-steg (WINE-25/WINE-38) - hur många
 * nya viner som sparades, och hur många rader som hoppades över
 * (kunde inte tolkas, eller var dubbletter av befintliga viner).
 */
record ImportResult(int imported, int skipped) {
}
