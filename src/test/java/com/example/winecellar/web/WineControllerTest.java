package com.example.winecellar.web;

import com.example.winecellar.application.HärkomstNod;
import com.example.winecellar.application.SorteringsRiktning;
import com.example.winecellar.application.Sorteringsfält;
import com.example.winecellar.application.Sökkriterier;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Rating;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;
import com.example.winecellar.domain.WineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testar bara webblagret: WineService är stubbad, så det som verifieras är
 * den faktiskt renderade HTML:en (formulärfält, htmx-attribut, listfragmentet)
 * och åtkomstskyddet - inte affärslogiken, som redan täcks av
 * acceptanstesterna i features/.
 *
 * @TestPropertySource pinnar admin-lösenordet till "admin" oavsett vad som
 * faktiskt är satt i miljön testet körs i. Utan detta läcker Clever Clouds
 * WINECELLAR_ADMIN_PASSWORD (satt för produktionsappen) in i byggsteget och
 * skriver över application.ymls lokala default - alla httpBasic("admin",
 * "admin")-anrop nedan börjar då få 401 mot det riktiga produktionslösenordet,
 * vilket kraschade en hel deploy (se git-historiken/CLAUDE.md).
 */
@WebMvcTest(WineController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "winecellar.admin.password=admin")
class WineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WineService wineService;

    private static final Wine BAROLO = Wine.builder()
            .id(new WineId(1L)).name("Barolo").wineType(WineType.RED).producer("Pio Cesare").country("Italien")
            .vintage(2018).quantity(3).location("Låda 1")
            .build();

    @Nested
    @DisplayName("utan inloggning")
    class UtanInloggning {

        @Test
        @DisplayName("ska GET / nekas")
        void skaGetNekas() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ska POST /wines nekas och aldrig nå WineService")
        void skaPostWinesNekas() throws Exception {
            mockMvc.perform(post("/wines")
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("vintage", "2018")
                            .param("quantity", "3")
                            .param("location", "Låda 1"))
                    .andExpect(status().isUnauthorized());

            verify(wineService, never()).save(any());
        }

        @Test
        @DisplayName("ska DELETE nekas")
        void skaDeleteNekas() throws Exception {
            mockMvc.perform(delete("/wines/1"))
                    .andExpect(status().isUnauthorized());

            verify(wineService, never()).removeWine(new WineId(1L));
        }

        @Test
        @DisplayName("ska bildvisning nekas")
        void skaBildvisningNekas() throws Exception {
            mockMvc.perform(get("/wines/1/bild"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ska redigeringsformuläret nekas")
        void skaRedigeringsformuläretNekas() throws Exception {
            mockMvc.perform(get("/wines/1/redigera"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ska sparad redigering nekas")
        void skaSparadRedigeringNekas() throws Exception {
            mockMvc.perform(post("/wines/1/redigera")
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("vintage", "2018")
                            .param("quantity", "3")
                            .param("location", "Låda 1"))
                    .andExpect(status().isUnauthorized());

            verify(wineService, never()).save(any());
        }

        @Test
        @DisplayName("ska formuläret för ett nytt vin nekas")
        void skaFormuläretFörEttNyttVinNekas() throws Exception {
            mockMvc.perform(get("/wines/nytt"))
                    .andExpect(status().isUnauthorized());
        }
    }

    /**
     * readonly/readonly (se SecurityConfig) - får se listan och bilder men
     * inte lägga till, redigera eller ta bort. Nekas både POST/DELETE-
     * routerna och GET-routerna för formulären (/wines/nytt,
     * /wines/{id}/redigera) - annars går det att komma åt formulärsidan
     * genom att bara gissa på URL:en, även om länken är dold i UI:t.
     */
    @Nested
    @DisplayName("readonly-kontot")
    class ReadonlyKontot {

        @Test
        @DisplayName("ska se listan utan länkar/knappar för lägg till, redigera eller ta bort")
        void skaSeListanUtanRedigeringslänkar() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(httpBasic("readonly", "readonly")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Barolo"),
                            not(containsString("href=\"/wines/nytt\"")),
                            not(containsString("class=\"detalj-atgarder\""))
                    )));
        }

        @Test
        @DisplayName("ska nekas formuläret för ett nytt vin")
        void skaNekasFormuläretFörEttNyttVin() throws Exception {
            mockMvc.perform(get("/wines/nytt").with(httpBasic("readonly", "readonly")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ska nekas att lägga till ett vin och aldrig nå WineService")
        void skaNekasAttLäggaTillEttVin() throws Exception {
            mockMvc.perform(post("/wines")
                            .with(httpBasic("readonly", "readonly"))
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("vintage", "2018")
                            .param("quantity", "3")
                            .param("location", "Låda 1"))
                    .andExpect(status().isForbidden());

            verify(wineService, never()).save(any());
        }

        @Test
        @DisplayName("ska nekas redigeringsformuläret")
        void skaNekasRedigeringsformuläret() throws Exception {
            mockMvc.perform(get("/wines/1/redigera").with(httpBasic("readonly", "readonly")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ska nekas att spara en redigering och aldrig nå WineService")
        void skaNekasAttSparaEnRedigering() throws Exception {
            mockMvc.perform(post("/wines/1/redigera")
                            .with(httpBasic("readonly", "readonly"))
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("vintage", "2018")
                            .param("quantity", "3")
                            .param("location", "Låda 1"))
                    .andExpect(status().isForbidden());

            verify(wineService, never()).save(any());
        }

        @Test
        @DisplayName("ska nekas att ta bort ett vin och aldrig nå WineService")
        void skaNekasAttTaBortEttVin() throws Exception {
            mockMvc.perform(delete("/wines/1").with(httpBasic("readonly", "readonly")))
                    .andExpect(status().isForbidden());

            verify(wineService, never()).removeWine(new WineId(1L));
        }

        @Test
        @DisplayName("ska ändå få se en bild")
        void skaFåSeEnBild() throws Exception {
            byte[] bilddata = new byte[]{1, 2, 3};
            when(wineService.findById(new WineId(1L)))
                    .thenReturn(Optional.of(BAROLO.withImage(bilddata, "image/jpeg")));

            mockMvc.perform(get("/wines/1/bild").with(httpBasic("readonly", "readonly")))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("image/jpeg"));
        }
    }

    @Nested
    @DisplayName("startsidan")
    class Startsidan {

        @Test
        @DisplayName("ska lista befintliga viner och länka till formuläret för ett nytt vin")
        void skaListaBefintligaVinerOchLänkaTillNyttVinFormulär() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("href=\"/wines/nytt\""),
                            containsString("Barolo")
                    )));
        }

        @Test
        @DisplayName("ska rendera ett vin som bara har namnet ifyllt utan att krascha (typ/årgång/antal/land/producent/plats null)")
        void skaRenderaEttVinMedBaraNamnetIfylltUtanAttKrascha() throws Exception {
            Wine minimaltVin = Wine.builder().id(new WineId(1L)).name("Anteckning om ett vin").build();
            when(wineService.sök(any())).thenReturn(List.of(minimaltVin));

            mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Anteckning om ett vin")));
        }

        @Test
        @DisplayName("kortvyn (mobil) ska visa geografi- och betygsfält i översikten, resten infällt under \"Detaljer\"")
        void skaVisaFältUppdeladeMellanÖversiktOchDetaljer() throws Exception {
            Wine barolo = BAROLO.toBuilder()
                    .region("Piemonte").subregion("Langhe").grapes("Nebbiolo")
                    .purchaseDate(LocalDate.of(2024, 3, 15)).price(new BigDecimal("450.00"))
                    .purchaseReason("Rekommenderat").tastingNotes("Kraftfullt")
                    .ownRating(Rating.R16)
                    .systembolagetProductNumber("12345").systembolagetDescription("Beskrivning")
                    .munskankarnaReview("Recension").munskankarnaRating(Rating.R14_5)
                    .vivinoRating(new BigDecimal("4.1")).otherReference("https://example.com")
                    .build();
            when(wineService.sök(any())).thenReturn(List.of(barolo));

            mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            // Översikten - region/underregion/druvor och betyg
                            containsString("Piemonte"),
                            containsString("Langhe"),
                            containsString("Nebbiolo"),
                            containsString(Rating.R16.label()),
                            containsString(Rating.R14_5.label()),
                            containsString("4.1"),
                            // Infällt under "Detaljer" - plats och övriga fält
                            containsString("Detaljer"),
                            containsString("Låda 1"),
                            containsString("2024-03-15"),
                            containsString("450.00 kr"),
                            containsString("Rekommenderat"),
                            containsString("Kraftfullt"),
                            // Systembolagets produktnummer visas inte längre som en egen
                            // rad - värdet står inom parentes direkt efter beskrivnings-
                            // etiketten istället
                            containsString("Systembolagets beskrivning (12345)"),
                            not(containsString("Systembolagets produktnummer")),
                            containsString("Beskrivning"),
                            containsString("Recension"),
                            containsString("https://example.com")
                    )));
        }

        @Test
        @DisplayName("ska dölja produktnumret helt om beskrivningen saknas, eftersom det bara visas som en parentes på beskrivningsraden")
        void skaDöljaProduktnummerOmBeskrivningSaknas() throws Exception {
            Wine barolo = BAROLO.toBuilder()
                    .systembolagetProductNumber("12345")
                    .build();
            when(wineService.sök(any())).thenReturn(List.of(barolo));

            mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            not(containsString("Systembolagets beskrivning")),
                            not(containsString("12345"))
                    )));
        }

        @Test
        @DisplayName("ska rendera kortvyns badge, staplade betygs-/detaljfält och flytta åtgärderna in i Detaljer")
        void skaRenderaKortvynsNyaStruktur() throws Exception {
            Wine barolo = BAROLO.toBuilder()
                    .purchaseReason("Rekommenderat")
                    .tastingNotes("Kraftfullt")
                    .ownRating(Rating.R16)
                    .build();
            when(wineService.sök(any())).thenReturn(List.of(barolo));

            mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            // Flaskbadge och kortets fältetiketslösa struktur (se vinkallare.html)
                            containsString("class=\"flaskor-badge\""),
                            containsString("class=\"vinkort-producent\""),
                            containsString("class=\"vinkort-namn\""),
                            // Betygsraderna har etikett och värde som separata element
                            // (staplas via CSS i kortvyn) istället för en enda textrad
                            containsString("class=\"betyg-label\""),
                            containsString("class=\"betyg-varde\""),
                            // Detaljfältens fd-*-klasser styr ordning/stapling i kortvyn
                            // via CSS (se .vinkort dl-reglerna) utan att ändra
                            // detaljfalt-fragmentets DOM-ordning
                            containsString("class=\"fd-varfor-kopt\""),
                            containsString("class=\"fd-tasting\""),
                            // Redigera/Ta bort ligger numera inne i Detaljer, inte i
                            // översikten - .detalj-atgarder delas mellan de breda korten
                            // och kortvyn
                            containsString("class=\"detalj-atgarder\"")
                    )));
        }

        @Test
        @DisplayName("de breda korten (desktop) ska visa alla fält direkt, utan någon infälld \"Detaljer\"")
        void skaRenderaBredaKortMedAllaFältSynliga() throws Exception {
            Wine barolo = BAROLO.toBuilder()
                    .region("Piemonte").subregion("Langhe").grapes("Nebbiolo")
                    .purchaseDate(LocalDate.of(2024, 3, 15)).price(new BigDecimal("450.00"))
                    .purchaseReason("Rekommenderat").tastingNotes("Kraftfullt")
                    .ownRating(Rating.R16)
                    .systembolagetProductNumber("12345").systembolagetDescription("Beskrivning")
                    .munskankarnaReview("Recension").munskankarnaRating(Rating.R14_5)
                    .vivinoRating(new BigDecimal("4.1")).otherReference("https://example.com")
                    .build();
            when(wineService.sök(any())).thenReturn(List.of(barolo));

            mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            // Den gamla <table>-baserade tabellvyn är helt borttagen
                            not(containsString("<table>")),
                            not(containsString("class=\"vinbild-tabell\"")),
                            // De breda kortens egna strukturklasser (se vinkallare.html)
                            containsString("class=\"vinkort-bred\""),
                            containsString("class=\"vk-topp\""),
                            containsString("class=\"vk-info-rad\""),
                            containsString("class=\"vk-text-rad\""),
                            containsString("class=\"vk-vivino\""),
                            containsString("class=\"vk-munskankarna\""),
                            containsString("class=\"vk-egetbetyg\""),
                            // Samtliga fält - inklusive Annan referens, som varken
                            // fanns i tabellvyn eller kortvyns Detaljer tidigare
                            containsString("Piemonte"),
                            containsString("Langhe"),
                            containsString("Nebbiolo"),
                            containsString("Låda 1"),
                            containsString("2024-03-15"),
                            containsString("450.00 kr"),
                            containsString("Rekommenderat"),
                            containsString("Kraftfullt"),
                            containsString("Systembolagets beskrivning (12345)"),
                            containsString("Recension"),
                            containsString("Annan referens"),
                            containsString("https://example.com")
                    )));
        }

        @Test
        @DisplayName("ska rendera sorteringskontroller med alla sorterbara fält")
        void skaRenderaSorteringskontrollerMedAllaFält() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("name=\"sortera\""),
                            containsString("name=\"riktning\""),
                            containsString("value=\"NAMN\""),
                            containsString("value=\"PRODUCENT\""),
                            containsString("value=\"LAND\""),
                            containsString("value=\"ARGANG\""),
                            containsString("value=\"ANTAL\""),
                            containsString("value=\"PRIS\""),
                            containsString("value=\"INKOPSDATUM\""),
                            containsString("value=\"EGET_BETYG\""),
                            containsString("value=\"MUNSKANKARNA_BETYG\""),
                            containsString("value=\"VIVINO_BETYG\""),
                            containsString("value=\"STIGANDE\""),
                            containsString("value=\"FALLANDE\"")
                    )));
        }

        @Test
        @DisplayName("ska sortera på namn, stigande, som standard när inget valts")
        void skaAnvändaStandardsortering() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk());

            verify(wineService).sök(Sökkriterier.builder()
                    .sortering(Sorteringsfält.NAMN).riktning(SorteringsRiktning.STIGANDE)
                    .build());
        }

        @Test
        @DisplayName("ska skicka valt sorteringsfält och riktning vidare till WineService")
        void skaSkickaValdSorteringTillWineService() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/")
                            .with(httpBasic("admin", "admin"))
                            .param("sortera", "EGET_BETYG")
                            .param("riktning", "FALLANDE"))
                    .andExpect(status().isOk());

            verify(wineService).sök(Sökkriterier.builder()
                    .sortering(Sorteringsfält.EGET_BETYG).riktning(SorteringsRiktning.FALLANDE)
                    .build());
        }

        @Test
        @DisplayName("ska returnera bara listfragmentet, inte hela sidan, vid en htmx-förfrågan")
        void skaReturneraBaraListfragmentetVidHtmxFörfrågan() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/")
                            .with(httpBasic("admin", "admin"))
                            .header("HX-Request", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("id=\"vinlista\""),
                            containsString("Barolo"),
                            not(containsString("<html")),
                            not(containsString("name=\"sortera\""))
                    )));
        }

        @Test
        @DisplayName("ska rendera kryssrutor för alla vintyper")
        void skaRenderaFilterkryssrutorMedAllaVintyper() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("name=\"wineType\" value=\"RED\""),
                            containsString("name=\"wineType\" value=\"WHITE\""),
                            containsString("name=\"wineType\" value=\"ROSE\""),
                            containsString("name=\"wineType\" value=\"SPARKLING\""),
                            containsString("name=\"wineType\" value=\"FORTIFIED\""),
                            containsString("Rött"),
                            containsString("Vitt"),
                            containsString("Rosé"),
                            containsString("Mousserande"),
                            containsString("Starkvin")
                    )));
        }

        @Test
        @DisplayName("filterpanelens knapp ska heta \"Dölj filter\", inte \"Använd filter\" - checkrutorna applicerar redan filtret vid ändring")
        void skaHaKnappenDöljFilter() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Dölj filter"),
                            not(containsString("Använd filter"))
                    )));
        }

        @Test
        @DisplayName("ska rendera härkomstträdet som nästlade kryssrutor för land/region/underregion")
        void skaRenderaHärkomstträdetSomNästladeKryssrutor() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));
            when(wineService.härkomstträd()).thenReturn(List.of(
                    new HärkomstNod("Italien", List.of(
                            new HärkomstNod("Piemonte", List.of(
                                    new HärkomstNod("Langhe", List.of())
                            ))
                    ))
            ));

            mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("name=\"country\" value=\"Italien\""),
                            containsString("name=\"region\" value=\"Piemonte\""),
                            containsString("name=\"subregion\" value=\"Langhe\"")
                    )));
        }

        @Test
        @DisplayName("ska fälla ut land- och regionnivån automatiskt runt en vald underregion")
        void skaFällaUtTrädetAutomatisktRuntEnValdUnderregion() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));
            when(wineService.härkomstträd()).thenReturn(List.of(
                    new HärkomstNod("Italien", List.of(
                            new HärkomstNod("Piemonte", List.of(
                                    new HärkomstNod("Langhe", List.of())
                            ))
                    )),
                    new HärkomstNod("Frankrike", List.of(
                            new HärkomstNod("Bourgogne", List.of())
                    ))
            ));

            String html = mockMvc.perform(get("/")
                            .with(httpBasic("admin", "admin"))
                            .param("subregion", "Langhe"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // Bara Italien- och Piemonte-nivån (som leder till den valda
            // underregionen Langhe) ska vara uppfällda - inte Frankrike/
            // Bourgogne, som inte har något valt under sig.
            int uppfällda = html.split("<details open=\"open\">", -1).length - 1;
            assertThat(uppfällda).isEqualTo(2);
        }

        @Test
        @DisplayName("ska hålla trädet hopfällt när inget filter är valt")
        void skaHållaTrädetHopfälltUtanValtFilter() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));
            when(wineService.härkomstträd()).thenReturn(List.of(
                    new HärkomstNod("Italien", List.of(
                            new HärkomstNod("Piemonte", List.of(
                                    new HärkomstNod("Langhe", List.of())
                            ))
                    ))
            ));

            String html = mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).doesNotContain("<details open=\"open\">");
        }

        @Test
        @DisplayName("ska skicka valda filter vidare till WineService")
        void skaSkickaValdaFilterTillWineService() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/")
                            .with(httpBasic("admin", "admin"))
                            .param("wineType", "RED", "WHITE")
                            .param("country", "Italien"))
                    .andExpect(status().isOk());

            verify(wineService).sök(Sökkriterier.builder()
                    .sortering(Sorteringsfält.NAMN).riktning(SorteringsRiktning.STIGANDE)
                    .vintyper(Set.of(WineType.RED, WineType.WHITE))
                    .länder(Set.of("Italien"))
                    .build());
        }

        @Test
        @DisplayName("ska förhandskryssa redan valda filter vid sidladdning")
        void skaFörhandskryssaRedanValdaFilter() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/")
                            .with(httpBasic("admin", "admin"))
                            .param("wineType", "RED"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            containsString("name=\"wineType\" value=\"RED\" checked")
                    ));
        }

        @Test
        @DisplayName("ska rendera sökfältet och skicka sökordet vidare till WineService")
        void skaSkickaSökordetTillWineService() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/")
                            .with(httpBasic("admin", "admin"))
                            .param("sok", "barolo"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            containsString("name=\"sok\"")
                    ));

            verify(wineService).sök(Sökkriterier.builder()
                    .sökterm("barolo")
                    .sortering(Sorteringsfält.NAMN).riktning(SorteringsRiktning.STIGANDE)
                    .build());
        }

        @Test
        @DisplayName("ska förhandsifylla sökfältet med det aktiva sökordet")
        void skaFörhandsifyllaSökfältet() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/")
                            .with(httpBasic("admin", "admin"))
                            .param("sok", "barolo"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            containsString("value=\"barolo\"")
                    ));
        }

        @Test
        @DisplayName("ska visa antal träffar av totalt antal viner")
        void skaVisaAntalTräffar() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));
            when(wineService.listWines()).thenReturn(List.of(BAROLO, BAROLO.toBuilder().name("Chablis").build()));

            mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Visar"),
                            containsString(">1<"),
                            containsString(">2<")
                    )));
        }

        @Test
        @DisplayName("ska inte visa några chips utan aktivt filter eller sökning")
        void skaInteVisaChipsUtanAktivtFilter() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("class=\"chip\""))));
        }

        @Test
        @DisplayName("ska visa en chip per aktivt filter-/sökvärde, vars borttagningslänk behåller övriga värden")
        void skaVisaChipsMedBorttagningslänkar() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            String html = mockMvc.perform(get("/")
                            .with(httpBasic("admin", "admin"))
                            .param("sok", "barolo")
                            .param("wineType", "RED")
                            .param("country", "Italien"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).contains("class=\"chip\"", "&quot;barolo&quot; ×", "Rött ×", "Italien ×");

            // "Rött"-chippens borttagningslänk ska ta bort wineType=RED,
            // men behålla sok och country oförändrade.
            java.util.regex.Matcher chipLänk = java.util.regex.Pattern
                    .compile("href=\"([^\"]+)\"[^>]*>Rött ×")
                    .matcher(html);
            assertThat(chipLänk.find()).isTrue();
            String href = chipLänk.group(1);
            assertThat(href).doesNotContain("wineType=RED")
                    .contains("country=Italien")
                    .contains("sok=barolo");
        }

        @Test
        @DisplayName("\"Ta bort\"-länken ska skicka med aktivt filter, sökning och sortering")
        void skaSkickaMedAktivtTillståndITaBortLänken() throws Exception {
            when(wineService.sök(any())).thenReturn(List.of(BAROLO));

            String html = mockMvc.perform(get("/")
                            .with(httpBasic("admin", "admin"))
                            .param("sok", "barolo")
                            .param("wineType", "RED")
                            .param("sortera", "ARGANG")
                            .param("riktning", "FALLANDE"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            java.util.regex.Matcher taBortLänk = java.util.regex.Pattern
                    .compile("hx-delete=\"([^\"]+)\"")
                    .matcher(html);
            assertThat(taBortLänk.find()).isTrue();
            String hxDelete = taBortLänk.group(1);
            assertThat(hxDelete).contains("sok=barolo")
                    .contains("wineType=RED")
                    .contains("sortera=ARGANG")
                    .contains("riktning=FALLANDE");
        }
    }

    @Nested
    @DisplayName("när formuläret för ett nytt vin visas")
    class NärFormuläretFörEttNyttVinVisas {

        @Test
        @DisplayName("ska formuläret vara tomt")
        void skaFormuläretVaraTomt() throws Exception {
            mockMvc.perform(get("/wines/nytt").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("action=\"/wines\""),
                            containsString("enctype=\"multipart/form-data\""),
                            containsString("name=\"name\""),
                            containsString("name=\"region\""),
                            containsString("name=\"ownRating\""),
                            containsString("name=\"bild\""),
                            containsString("Lägg till")
                    )));
        }
    }

    @Nested
    @DisplayName("när ett vin läggs till")
    class NärEttVinLäggsTill {

        @Test
        @DisplayName("ska vinet skickas till WineService och sidan omdirigera till startsidan")
        void skaSkickasTillServiceOchOmdirigera() throws Exception {
            mockMvc.perform(post("/wines")
                            .with(httpBasic("admin", "admin"))
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("vintage", "2018")
                            .param("quantity", "3")
                            .param("location", "Låda 1"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));

            verify(wineService).save(Wine.builder()
                    .name("Barolo").wineType(WineType.RED).producer("Pio Cesare").country("Italien")
                    .vintage(2018).quantity(3).location("Låda 1")
                    .build());
        }

        @Test
        @DisplayName("ska gå att lägga till ett vin med bara namnet ifyllt - övriga fält blir null, inte tomma strängar/0")
        void skaGåAttLäggaTillMedBaraNamnet() throws Exception {
            mockMvc.perform(post("/wines")
                            .with(httpBasic("admin", "admin"))
                            .param("name", "Anteckning om ett vin"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));

            verify(wineService).save(Wine.builder()
                    .name("Anteckning om ett vin")
                    .build());
        }

        @Test
        @DisplayName("ska bilden sparas tillsammans med resten av vinet om en fil valdes")
        void skaBildenSparasTillsammansMedResten() throws Exception {
            byte[] bilddata = new byte[]{1, 2, 3};

            mockMvc.perform(multipart("/wines")
                            .file(new MockMultipartFile("bild", "etikett.jpg", "image/jpeg", bilddata))
                            .with(httpBasic("admin", "admin"))
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("vintage", "2018")
                            .param("quantity", "3")
                            .param("location", "Låda 1"))
                    .andExpect(status().is3xxRedirection());

            verify(wineService).save(Wine.builder()
                    .name("Barolo").wineType(WineType.RED).producer("Pio Cesare").country("Italien")
                    .vintage(2018).quantity(3).location("Låda 1")
                    .image(bilddata).imageMimeType("image/jpeg")
                    .build());
        }
    }

    @Nested
    @DisplayName("när ett vin tas bort")
    class NärEttVinTasBort {

        @Test
        @DisplayName("ska id skickas till WineService")
        void skaIdSkickasTillService() throws Exception {
            when(wineService.listWines()).thenReturn(List.of());

            mockMvc.perform(delete("/wines/1").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk());

            verify(wineService).removeWine(new WineId(1L));
        }

        @Test
        @DisplayName("ska behålla aktivt filter, sökning och sortering efter en borttagning")
        void skaBehållaAktivtFilterEfterBorttagning() throws Exception {
            when(wineService.listWines()).thenReturn(List.of());
            when(wineService.sök(any())).thenReturn(List.of());

            mockMvc.perform(delete("/wines/1")
                            .with(httpBasic("admin", "admin"))
                            .param("sok", "barolo")
                            .param("wineType", "RED")
                            .param("sortera", "ARGANG")
                            .param("riktning", "FALLANDE"))
                    .andExpect(status().isOk());

            verify(wineService).sök(Sökkriterier.builder()
                    .sökterm("barolo")
                    .sortering(Sorteringsfält.ARGANG).riktning(SorteringsRiktning.FALLANDE)
                    .vintyper(Set.of(WineType.RED))
                    .build());
        }
    }

    @Nested
    @DisplayName("när en bild visas")
    class NärEnBildVisas {

        @Test
        @DisplayName("ska bilden serveras med rätt Content-Type")
        void skaBildenSererasMedRättContentType() throws Exception {
            byte[] bilddata = new byte[]{1, 2, 3};
            when(wineService.findById(new WineId(1L)))
                    .thenReturn(Optional.of(BAROLO.withImage(bilddata, "image/jpeg")));

            mockMvc.perform(get("/wines/1/bild").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("image/jpeg"))
                    .andExpect(content().bytes(bilddata));
        }

        @Test
        @DisplayName("ska ge 404 om vinet saknar bild")
        void skaGe404OmVinetSaknarBild() throws Exception {
            when(wineService.findById(new WineId(1L))).thenReturn(Optional.of(BAROLO));

            mockMvc.perform(get("/wines/1/bild").with(httpBasic("admin", "admin")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("när redigeringsformuläret visas")
    class NärRedigeringsformuläretVisas {

        @Test
        @DisplayName("ska formuläret vara förifyllt med vinets uppgifter")
        void skaFormuläretVaraFörifylltMedVinetsUppgifter() throws Exception {
            when(wineService.findById(new WineId(1L))).thenReturn(Optional.of(BAROLO));

            mockMvc.perform(get("/wines/1/redigera").with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("enctype=\"multipart/form-data\""),
                            containsString("value=\"Barolo\""),
                            containsString("name=\"region\""),
                            containsString("name=\"purchaseDate\""),
                            containsString("name=\"price\""),
                            containsString("name=\"ownRating\""),
                            containsString("name=\"systembolagetProductNumber\""),
                            containsString("name=\"munskankarnaRating\""),
                            containsString("name=\"vivinoRating\""),
                            containsString("name=\"bild\""),
                            containsString(Rating.R16.label())
                    )));
        }
    }

    @Nested
    @DisplayName("när ett vin redigeras")
    class NärEttVinRedigeras {

        @Test
        @DisplayName("ska alla fält skickas till WineService och sidan omdirigera till startsidan")
        void skaAllaFältSkickasTillServiceOchOmdirigera() throws Exception {
            when(wineService.findById(new WineId(1L))).thenReturn(Optional.of(BAROLO));

            mockMvc.perform(post("/wines/1/redigera")
                            .with(httpBasic("admin", "admin"))
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("region", "Piemonte")
                            .param("subregion", "Langhe")
                            .param("grapes", "Nebbiolo")
                            .param("vintage", "2018")
                            .param("purchaseDate", "2024-03-15")
                            .param("price", "450.00")
                            .param("quantity", "3")
                            .param("purchaseReason", "Rekommenderat")
                            .param("tastingNotes", "Kraftfullt")
                            .param("ownRating", "R16")
                            .param("systembolagetProductNumber", "12345")
                            .param("systembolagetDescription", "Beskrivning")
                            .param("munskankarnaReview", "Recension")
                            .param("munskankarnaRating", "R14_5")
                            .param("vivinoRating", "4.1")
                            .param("otherReference", "https://example.com")
                            .param("location", "Låda 2"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));

            verify(wineService).save(BAROLO.toBuilder()
                    .region("Piemonte").subregion("Langhe").grapes("Nebbiolo")
                    .purchaseDate(LocalDate.of(2024, 3, 15)).price(new BigDecimal("450.00"))
                    .purchaseReason("Rekommenderat").tastingNotes("Kraftfullt")
                    .ownRating(Rating.R16)
                    .systembolagetProductNumber("12345").systembolagetDescription("Beskrivning")
                    .munskankarnaReview("Recension").munskankarnaRating(Rating.R14_5)
                    .vivinoRating(new BigDecimal("4.1")).otherReference("https://example.com")
                    .location("Låda 2")
                    .build());
        }

        @Test
        @DisplayName("ska lämna valfria fält som null när de inte fylls i")
        void skaLämnaValfriaFältSomNullNärDeInteFyllsI() throws Exception {
            when(wineService.findById(new WineId(1L))).thenReturn(Optional.of(BAROLO));

            mockMvc.perform(post("/wines/1/redigera")
                            .with(httpBasic("admin", "admin"))
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("vintage", "2018")
                            .param("quantity", "3")
                            .param("location", "Låda 1"))
                    .andExpect(status().is3xxRedirection());

            verify(wineService).save(BAROLO);
        }

        @Test
        @DisplayName("ska ersätta bilden om en ny fil väljs")
        void skaErsättaBildenOmEnNyFilVäljs() throws Exception {
            when(wineService.findById(new WineId(1L))).thenReturn(Optional.of(BAROLO));
            byte[] nyBilddata = new byte[]{4, 5, 6};

            mockMvc.perform(multipart("/wines/1/redigera")
                            .file(new MockMultipartFile("bild", "ny-etikett.jpg", "image/jpeg", nyBilddata))
                            .with(httpBasic("admin", "admin"))
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("vintage", "2018")
                            .param("quantity", "3")
                            .param("location", "Låda 1"))
                    .andExpect(status().is3xxRedirection());

            verify(wineService).save(BAROLO.withImage(nyBilddata, "image/jpeg"));
        }

        @Test
        @DisplayName("ska behålla befintlig bild om ingen ny fil väljs")
        void skaBehållaBefintligBildOmIngenNyFilVäljs() throws Exception {
            byte[] befintligBilddata = new byte[]{1, 2, 3};
            Wine vinMedBild = BAROLO.withImage(befintligBilddata, "image/jpeg");
            when(wineService.findById(new WineId(1L))).thenReturn(Optional.of(vinMedBild));

            mockMvc.perform(post("/wines/1/redigera")
                            .with(httpBasic("admin", "admin"))
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("vintage", "2018")
                            .param("quantity", "3")
                            .param("location", "Låda 1"))
                    .andExpect(status().is3xxRedirection());

            verify(wineService).save(vinMedBild);
        }
    }
}
