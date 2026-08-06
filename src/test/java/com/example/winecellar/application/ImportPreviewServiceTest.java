package com.example.winecellar.application;

import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Testar {@link ImportPreviewService}s nya interna dubblettkontroll
 * rad-mot-rad inom samma fil (WINE-34). Databas-dubblettkontrollen är
 * mockad eftersom den testas separat; fokus här är att enbart rader med
 * ömsesidigt matchande identitet flaggas, och att båda raderna i ett
 * dubblettpar räknas som felaktiga.
 */
@ExtendWith(MockitoExtension.class)
class ImportPreviewServiceTest {

    @Mock
    private WineService wineService;

    private ImportPreviewService service;

    private static final UserId OWNER = new UserId(42L);

    @BeforeEach
    void setUp() {
        service = new ImportPreviewService(wineService);
    }

    private void stubbaIngaDubbletter() {
        when(wineService.checkForDuplicate(any(), eq(OWNER))).thenReturn(new DuplicateCheck.NoDuplicate());
    }

    @Test
    void skaFlaggaBådaRadernaVidInternDubblett() {
        List<RowCandidate> candidates = List.of(
                candidate(2, "Same Name", "Producer A", 2020),
                candidate(3, "Same Name", "Producer A", 2020));

        ImportPreview preview = service.preview(candidates, List.of(), OWNER);

        assertThat(preview.issues()).extracting(RowIssue::message).containsExactlyInAnyOrder(
                "Rad 2: Vinet är en dublett av ett annat vin (rad 3)",
                "Rad 3: Vinet är en dublett av ett annat vin (rad 2)");
        assertThat(preview.fullDuplicates()).isZero();
        assertThat(preview.partialDuplicates()).isZero();
        assertThat(preview.clean()).isZero();
    }

    @Test
    void skaFlaggaSamtligaRaderVidInternDubblettgruppMedFlerÄnTvå() {
        List<RowCandidate> candidates = List.of(
                candidate(2, "Same Name", "Producer A", 2020),
                candidate(3, "Same Name", "Producer A", 2020),
                candidate(4, "Same Name", "Producer A", 2020));

        ImportPreview preview = service.preview(candidates, List.of(), OWNER);

        assertThat(preview.issues()).hasSize(3);
        assertThat(preview.issues()).extracting(RowIssue::rowNumber).containsExactlyInAnyOrder(2, 3, 4);
        assertThat(preview.clean()).isZero();
    }

    @Test
    void skaInteFlaggaRaderMedOlikaIdentitet() {
        stubbaIngaDubbletter();
        List<RowCandidate> candidates = List.of(
                candidate(2, "Name A", "Producer A", 2020),
                candidate(3, "Name B", "Producer B", 2021));

        ImportPreview preview = service.preview(candidates, List.of(), OWNER);

        assertThat(preview.issues()).isEmpty();
        assertThat(preview.clean()).isEqualTo(2);
    }

    @Test
    void skaBevaraParsningsfelOchLäggaTillDubblettfel() {
        List<RowIssue> parseIssues = List.of(new RowIssue(5, "Rad 5: Vinet måste ha ett namn"));
        List<RowCandidate> candidates = List.of(
                candidate(2, "Same Name", "Producer A", 2020),
                candidate(3, "Same Name", "Producer A", 2020));

        ImportPreview preview = service.preview(candidates, parseIssues, OWNER);

        assertThat(preview.issues()).extracting(RowIssue::message).contains(
                "Rad 5: Vinet måste ha ett namn",
                "Rad 2: Vinet är en dublett av ett annat vin (rad 3)",
                "Rad 3: Vinet är en dublett av ett annat vin (rad 2)");
        assertThat(preview.totalRows()).isEqualTo(3);
    }

    @Test
    void skaRäknaUnikaKandidaterMotDatabasDubblettkontrollen() {
        List<RowCandidate> candidates = List.of(
                candidate(2, "Barolo", "Pio Cesare", 2018));
        Wine existing = Wine.builder().id(new Wine.WineId(1L)).owner(OWNER).name("Barolo").producer("Pio Cesare").vintage(2018).quantity(1).build();
        when(wineService.checkForDuplicate(any(), eq(OWNER))).thenReturn(new DuplicateCheck.FullDuplicate(existing));

        ImportPreview preview = service.preview(candidates, List.of(), OWNER);

        assertThat(preview.issues()).isEmpty();
        assertThat(preview.fullDuplicates()).isEqualTo(1);
    }

    private RowCandidate candidate(int rowNumber, String name, String producer, int vintage) {
        return new RowCandidate(rowNumber, Wine.builder()
                .name(name)
                .producer(producer)
                .vintage(vintage)
                .quantity(1)
                .build());
    }

    @Test
    void excludeFileDuplicatesSkaAvlägsnaBådaRadernaIDubblettpar() {
        List<RowCandidate> candidates = List.of(
                candidate(2, "Same Name", "Producer A", 2020),
                candidate(3, "Same Name", "Producer A", 2020));
        List<RowIssue> issues = new ArrayList<>();

        List<RowCandidate> unique = service.excludeFileDuplicates(candidates, issues);

        assertThat(unique).isEmpty();
        assertThat(issues).extracting(RowIssue::message).containsExactlyInAnyOrder(
                "Rad 2: Vinet är en dublett av ett annat vin (rad 3)",
                "Rad 3: Vinet är en dublett av ett annat vin (rad 2)");
    }

    @Test
    void excludeFileDuplicatesSkaBehållaUnikaRader() {
        List<RowCandidate> candidates = List.of(
                candidate(2, "Name A", "Producer A", 2020),
                candidate(3, "Name B", "Producer B", 2021));
        List<RowIssue> issues = new ArrayList<>();

        List<RowCandidate> unique = service.excludeFileDuplicates(candidates, issues);

        assertThat(unique).hasSize(2);
        assertThat(issues).isEmpty();
    }
}
