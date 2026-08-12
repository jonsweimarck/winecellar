package com.example.winecellar.application;

import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * WINE-24/WINE-38 (se ADR 0014 och ADR 0018): kontrollerar redan tolkade
 * rader (parsning av själva xlsx-filen är
 * {@link com.example.winecellar.infrastructure.excel.WineRowParser}s
 * jobb, infrastrukturlagret - den här klassen tar bara emot redan tolkade
 * {@link RowCandidate}) mot dubblettkontrollen som redan finns i
 * {@link WineService} (WINE-6), utan att spara något. Orkestrering hör
 * hemma i applikationslagret, inte i controllern (samma princip som
 * ADR 0006).
 *
 * WINE-34: innan databas-dubblettkontrollen körs en intern dubblettkontroll
 * rad-mot-rad inom samma fil. Alla rader i en sådan intern dubblettgrupp
 * räknas som felaktiga och rapporteras med radnummer till varandra.
 *
 * WINE-38: rader som är fullständiga eller möjliga dubbletter av
 * befintliga viner räknas också som felaktiga och rapporteras med
 * radnummer, istället för att kategoriseras separat och hanteras via
 * strategier vid commit.
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
     * mot (se {@link WineService#checkForDuplicate}), inte ett fält på
     * kandidaten själv.
     */
    public ImportPreview preview(List<RowCandidate> candidates, List<RowIssue> parseIssues, UserId owner) {
        List<RowIssue> issues = new ArrayList<>(parseIssues);
        List<RowCandidate> unique = excludeFileDuplicates(candidates, issues);

        int clean = 0;
        for (RowCandidate candidate : unique) {
            DuplicateCheck check = wineService.checkForDuplicate(candidate.wine(), owner);
            if (check instanceof DuplicateCheck.FullDuplicate) {
                issues.add(new RowIssue(candidate.rowNumber(),
                        "Rad " + candidate.rowNumber() + ": Vinet är en fullständig dubblett till ett befintligt vin"));
            } else if (check instanceof DuplicateCheck.PartialDuplicate) {
                issues.add(new RowIssue(candidate.rowNumber(),
                        "Rad " + candidate.rowNumber() + ": Vinet är en möjlig dubblett till ett befintligt vin"));
            } else {
                clean++;
            }
        }
        return new ImportPreview(candidates.size() + parseIssues.size(), issues, clean);
    }

    /**
     * Avlägsnar rader som är interna dubbletter av varandra inom samma
     * fil. Båda (eller alla) raderna i en sådan grupp räknas som
     * felaktiga; ingen av dem går vidare till databas-dubblettkontrollen.
     *
     * @return de kandidater som återstår efter att dubblettgrupper tagits bort
     */
    public List<RowCandidate> excludeFileDuplicates(List<RowCandidate> candidates, List<RowIssue> issues) {
        List<RowCandidate> unique = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            RowCandidate current = candidates.get(i);
            RowCandidate firstDuplicate = null;
            for (int j = 0; j < candidates.size(); j++) {
                if (i == j) {
                    continue;
                }
                RowCandidate other = candidates.get(j);
                if (current.wine().matchesIdentityOf(other.wine()) && other.wine().matchesIdentityOf(current.wine())) {
                    firstDuplicate = other;
                    break;
                }
            }
            if (firstDuplicate == null) {
                unique.add(current);
            } else {
                issues.add(new RowIssue(current.rowNumber(),
                        "Rad " + current.rowNumber() + ": Vinet är en dublett av ett annat vin (rad " + firstDuplicate.rowNumber() + ")"));
            }
        }
        return unique;
    }
}
