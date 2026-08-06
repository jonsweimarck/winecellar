package com.example.winecellar.application;

/**
 * Ett fel för en specifik rad i importfilen. Meddelandet är
 * färdigformaterat inklusive "Rad X: "-prefixet så att mallen kan
 * rendera det direkt.
 */
public record RowIssue(int rowNumber, String message) {
}
