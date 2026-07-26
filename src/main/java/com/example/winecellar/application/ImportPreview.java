package com.example.winecellar.application;

/**
 * Sammanfattningen av en torrkörning (WINE-24, se ADR 0014) - hur många
 * rader i den uppladdade filen som hoppas över (saknar namn, eller på
 * annat sätt inte kunde tolkas), är dubbletter (fullständiga/partiella,
 * se WINE-6) mot den inloggade användarens egna viner, eller rena nya
 * viner. Inget sparas när den här räknas fram - se
 * {@link ImportPreviewService}.
 */
public record ImportPreview(
        int totalRows,
        int skippedRows,
        int fullDuplicates,
        int partialDuplicates,
        int clean) {
}
