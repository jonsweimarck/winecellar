package com.example.winecellar.application;

import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * WINE-24 (se ADR 0014): kategoriserar redan tolkade rader (parsning av
 * själva xlsx-filen är `WineRowParser`s jobb, infrastrukturlagret - den
 * här klassen tar bara emot redan tolkade `Wine`-kandidater) mot
 * dubblettkontrollen som redan finns i {@link WineService} (WINE-6),
 * utan att spara något. Orkestrering hör hemma i applikationslagret,
 * inte controllern (samma princip som ADR 0006).
 */
@Service
public class ImportPreviewService {

    private final WineService wineService;

    public ImportPreviewService(WineService wineService) {
        this.wineService = wineService;
    }

    /**
     * `candidates` ska INTE ha `owner` satt - det är bara `owner`-
     * parametern som avgör vilka befintliga viner kandidaterna jämförs
     * mot (se `WineService.checkForDuplicate`), inte ett fält på
     * kandidaten själv.
     */
    public ImportPreview preview(List<Wine> candidates, int skippedRows, UserId owner) {
        int fullDuplicates = 0;
        int partialDuplicates = 0;
        int clean = 0;
        for (Wine candidate : candidates) {
            DuplicateCheck check = wineService.checkForDuplicate(candidate, owner);
            if (check instanceof DuplicateCheck.FullDuplicate) {
                fullDuplicates++;
            } else if (check instanceof DuplicateCheck.PartialDuplicate) {
                partialDuplicates++;
            } else {
                clean++;
            }
        }
        return new ImportPreview(candidates.size() + skippedRows, skippedRows, fullDuplicates, partialDuplicates, clean);
    }
}
