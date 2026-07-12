package com.example.winecellar.web;

import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;
import com.example.winecellar.domain.WineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testar bara webblagret: WineService är stubbad, så det som verifieras är
 * den faktiskt renderade HTML:en (formulärfält, htmx-attribut, listfragmentet) -
 * inte affärslogiken, som redan täcks av acceptanstesterna i features/.
 */
@WebMvcTest(WineController.class)
class WineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WineService wineService;

    private static final Wine BAROLO =
            new Wine(new WineId(1L), "Barolo", WineType.RED, "Pio Cesare", "Italien", 2018, 3, "Låda 1");

    @Nested
    @DisplayName("startsidan")
    class Startsidan {

        @Test
        @DisplayName("ska visa formulärfält och lista befintliga viner")
        void skaVisaFormulärfältOchListaBefintligaViner() throws Exception {
            when(wineService.listWines()).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("hx-post=\"/wines\""),
                            containsString("name=\"name\""),
                            containsString("name=\"wineType\""),
                            containsString("name=\"producer\""),
                            containsString("name=\"country\""),
                            containsString("name=\"vintage\""),
                            containsString("name=\"quantity\""),
                            containsString("name=\"location\""),
                            containsString("Barolo")
                    )));
        }
    }

    @Nested
    @DisplayName("när ett vin läggs till")
    class NärEttVinLäggsTill {

        @Test
        @DisplayName("ska vinet skickas till WineService och listfragmentet visa det")
        void skaSkickasTillServiceOchVisaDet() throws Exception {
            when(wineService.listWines()).thenReturn(List.of(BAROLO));

            mockMvc.perform(post("/wines")
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("vintage", "2018")
                            .param("quantity", "3")
                            .param("location", "Låda 1"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Barolo")));

            verify(wineService).save(new Wine(null, "Barolo", WineType.RED, "Pio Cesare", "Italien", 2018, 3, "Låda 1"));
        }
    }

    @Nested
    @DisplayName("när antalet flaskor ändras")
    class NärAntaletFlaskorÄndras {

        @Test
        @DisplayName("ska den uppdaterade mängden skickas till WineService")
        void skaDenUppdateradeMängdenSkickasTillService() throws Exception {
            when(wineService.findById(new WineId(1L))).thenReturn(Optional.of(BAROLO));
            when(wineService.listWines()).thenReturn(List.of(BAROLO.withQuantity(2)));

            mockMvc.perform(post("/wines/1/antal").param("quantity", "2"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Barolo")));

            verify(wineService).save(BAROLO.withQuantity(2));
        }
    }

    @Nested
    @DisplayName("när ett vin tas bort")
    class NärEttVinTasBort {

        @Test
        @DisplayName("ska id skickas till WineService")
        void skaIdSkickasTillService() throws Exception {
            when(wineService.listWines()).thenReturn(List.of());

            mockMvc.perform(delete("/wines/1"))
                    .andExpect(status().isOk());

            verify(wineService).removeWine(new WineId(1L));
        }
    }
}
