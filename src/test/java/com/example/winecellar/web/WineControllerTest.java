package com.example.winecellar.web;

import com.example.winecellar.application.DuplicateCheck;
import com.example.winecellar.application.LabelInterpretationResult;
import com.example.winecellar.application.LabelInterpretationService;
import com.example.winecellar.application.OriginNode;
import com.example.winecellar.application.SortDirection;
import com.example.winecellar.application.SortField;
import com.example.winecellar.application.SearchCriteria;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Rating;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;
import com.example.winecellar.domain.WineType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testar bara webblagret: WineService är stubbad, så det som verifieras är
 * den faktiskt renderade HTML:en (formulärfält, htmx-attribut, listfragmentet)
 * och åtkomstskyddet - inte affärslogiken, som redan täcks av
 * acceptanstesterna i features/.
 *
 * De flesta testerna använder bara {@code user(...)} - en ren
 * SecurityContext-injicering som INTE går via den riktiga
 * `UserDetailsService`n/lösenordskontrollen. Medvetet: dessa 40+ tester
 * bryr sig om `WineController`s renderings-/åtkomstlogik, inte om
 * autentiseringsmekaniken - att låta var och en av dem göra en riktig
 * inloggningsrundtur hade varit onödigt dyrt och inte testat något nytt.
 * Den riktiga inloggningsvägen (formLogin mot den faktiska konfigurationen,
 * inklusive `SecurityConfig`s riktiga `UserDetailsService`) testas istället
 * samlat i {@code InloggningOchUtloggning}, mot ett `User`
 * (`userRepository`-mocken) stubbat med ett riktigt hashat testlösenord -
 * sedan WINE-15 (admin/readonly borttagna) finns inget hårdkodat konto
 * kvar att logga in som.
 *
 * WINE-12 (formulärinloggning ersätter HTTP Basic) slog på CSRF igen -
 * varje POST/DELETE/multipart-anrop nedan har därför ett explicit
 * {@code .with(csrf())} utöver {@code user(...)}/inget alls. Ett försök att
 * lösa detta en gång för alla via en {@code MockMvcBuilderCustomizer}-bean
 * (defaultRequest) gav {@code @MockBean}-läckage mellan tester (stubbning
 * från ett test smittade nästa) - orsaken oklar, men det explicita mönstret
 * här är beprövat säkert.
 */
@WebMvcTest(WineController.class)
@Import(SecurityConfig.class)
// Produktionens default är medvetet TOM (se SecurityConfig/application.yml) -
// utan ett pinnat testvärde här skulle remember-me-stödet inte registreras
// alls i testkontexten, och HållMigInloggad-testerna nedan skulle sluta
// sätta någon cookie. Samma mönster som CLAUDE.md redan beskriver för andra
// hårdkodade testuppgifter i @WebMvcTest-klasser.
@TestPropertySource(properties = "winecellar.remember-me.key=test-remember-me-nyckel")
class WineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WineService wineService;

    @MockBean
    private LabelInterpretationService labelInterpretationService;

    /** SecurityConfigs userDetailsService-bean beror på UserRepository sedan WINE-11. */
    @MockBean
    private UserRepository userRepository;

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
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }

        @Test
        @DisplayName("ska POST /wines nekas och aldrig nå WineService")
        void skaPostWinesNekas() throws Exception {
            mockMvc.perform(post("/wines")
                            .with(csrf())
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("vintage", "2018")
                            .param("quantity", "3")
                            .param("location", "Låda 1"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));

            verify(wineService, never()).save(any());
        }

        @Test
        @DisplayName("ska radering nekas")
        void skaRaderaNekas() throws Exception {
            mockMvc.perform(post("/wines/1/radera").with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));

            verify(wineService, never()).removeWine(any(), any());
        }

        @Test
        @DisplayName("ska bildvisning nekas")
        void skaBildvisningNekas() throws Exception {
            mockMvc.perform(get("/wines/1/bild"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }

        @Test
        @DisplayName("ska redigeringsformuläret nekas")
        void skaRedigeringsformuläretNekas() throws Exception {
            mockMvc.perform(get("/wines/1/redigera"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }

        @Test
        @DisplayName("ska sparad redigering nekas")
        void skaSparadRedigeringNekas() throws Exception {
            mockMvc.perform(post("/wines/1/redigera")
                            .with(csrf())
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("vintage", "2018")
                            .param("quantity", "3")
                            .param("location", "Låda 1"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));

            verify(wineService, never()).save(any());
        }

        @Test
        @DisplayName("ska formuläret för ett nytt vin nekas")
        void skaFormuläretFörEttNyttVinNekas() throws Exception {
            mockMvc.perform(get("/wines/nytt"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
    }

    /**
     * WINE-12: den riktiga inloggnings-/utloggningsvägen, mot den faktiska
     * `UserDetailsService`n/lösenordskontrollen (formLogin, inte
     * user(...)) - se klasskommentaren för varför bara den här klassen gör
     * det. Motsvarar de tre Given/När/Så-scenarierna i WINE-12.
     *
     * Sedan WINE-15 (admin/readonly borttagna) finns inget hårdkodat
     * konto att logga in som - `userRepository`-mocken stubbas här med
     * ett `User` vars lösenord faktiskt är hashat med den riktiga
     * `PasswordEncoder`-beanen, så `SecurityConfig`s `UserDetailsService`
     * (som frågar samma mock) kan autentisera det på riktigt.
     */
    @Nested
    @DisplayName("inloggning och utloggning")
    class InloggningOchUtloggning {

        @Autowired
        private PasswordEncoder passwordEncoder;

        @BeforeEach
        void stubbaTestanvändare() {
            User testAnvändare = new User(
                    new UserId(1L), "testperson", passwordEncoder.encode("hemligt123"), Instant.now(), 0, false);
            when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(testAnvändare));
        }

        @Test
        @DisplayName("ska logga in med rätt uppgifter och komma till startsidan")
        void skaLoggaInMedRättaUppgifter() throws Exception {
            mockMvc.perform(formLogin().user("testperson").password("hemligt123"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));
        }

        @Test
        @DisplayName("ska nekas inloggning med fel lösenord och stanna kvar på inloggningssidan")
        void skaNekasInloggningMedFelLösenord() throws Exception {
            mockMvc.perform(formLogin().user("testperson").password("fel"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));
        }

        @Test
        @DisplayName("ska avsluta sessionen vid utloggning, så startsidan kräver ny inloggning")
        void skaAvslutaSessionenVidUtloggning() throws Exception {
            MvcResult inloggning = mockMvc.perform(formLogin().user("testperson").password("hemligt123"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"))
                    .andReturn();
            MockHttpSession session = (MockHttpSession) inloggning.getRequest().getSession(false);

            mockMvc.perform(post("/logout").session(session).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?logout"));

            mockMvc.perform(get("/").session(session))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }

        /**
         * WINE-40: "håll mig inloggad"-kryssrutan postar som
         * `remember-me` - Spring Securitys egen defaultparameter, som
         * kryssrutan i login.html medvetet återanvänder i stället för ett
         * eget namn.
         */
        @Nested
        @DisplayName("håll mig inloggad")
        class HållMigInloggad {

            @Test
            @DisplayName("ska sätta en remember-me-cookie när rutan är ikryssad")
            void skaSättaRememberMeCookieNärRutanÄrIkryssad() throws Exception {
                MvcResult inloggning = mockMvc.perform(post("/login")
                                .with(csrf())
                                .param("username", "testperson")
                                .param("password", "hemligt123")
                                .param("remember-me", "on"))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrl("/"))
                        .andReturn();

                Cookie rememberMeCookie = inloggning.getResponse().getCookie("remember-me");
                assertThat(rememberMeCookie).isNotNull();
                assertThat(rememberMeCookie.getValue()).isNotBlank();
                // Positiv maxAge = webbläsaren sparar den bortom sessionen
                // (dvs. även efter att fliken/webbläsaren stängs) -
                // exakt det en "session-only"-cookie (maxAge -1) INTE gör.
                assertThat(rememberMeCookie.getMaxAge()).isGreaterThan(0);
            }

            @Test
            @DisplayName("ska INTE sätta någon remember-me-cookie när rutan lämnas okryssad")
            void skaInteSättaRememberMeCookieUtanIkryssadRuta() throws Exception {
                MvcResult inloggning = mockMvc.perform(post("/login")
                                .with(csrf())
                                .param("username", "testperson")
                                .param("password", "hemligt123"))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrl("/"))
                        .andReturn();

                assertThat(inloggning.getResponse().getCookie("remember-me")).isNull();
            }

            /**
             * Den egentliga poängen med funktionen: en HELT NY förfrågan
             * utan sessionscookie (som en webbläsare som stängts och
             * öppnats igen skulle skicka) ska ändå räknas som inloggad så
             * länge remember-me-cookien följer med. Ett test som bara
             * kollar att EN cookie sätts (ovan) bevisar inte att den
             * faktiskt fungerar för återautentisering.
             */
            @Test
            @DisplayName("ska hålla användaren inloggad via cookien, helt utan aktiv session")
            void skaHållaAnvändarenInloggadViaCookienUtanSession() throws Exception {
                MvcResult inloggning = mockMvc.perform(post("/login")
                                .with(csrf())
                                .param("username", "testperson")
                                .param("password", "hemligt123")
                                .param("remember-me", "on"))
                        .andExpect(status().is3xxRedirection())
                        .andReturn();
                Cookie rememberMeCookie = inloggning.getResponse().getCookie("remember-me");

                mockMvc.perform(get("/").cookie(rememberMeCookie))
                        .andExpect(status().isOk());
            }

            /**
             * Det hash-baserade läget (se ADR 0020) är helt tillståndslöst
             * server-side - det finns ingen lista över utfärdade cookies
             * att stryka en rad ur. Utloggning kan alltså bara instruera
             * webbläsaren att SJÄLV kasta cookien (en satt `Max-Age: 0`) -
             * en tidigare kopierad cookie-sträng (t.ex. om den läckt)
             * förblir giltig till sin egen utgångstid oavsett utloggning.
             * Testet verifierar därför bara det webbläsaren faktiskt kan
             * lita på: att en normal utloggning tar bort cookien från DEN
             * egna webbläsaren, inte en (omöjlig att uppnå utan en
             * databas) server-side återkallning.
             */
            @Test
            @DisplayName("ska instruera webbläsaren att ta bort remember-me-cookien vid utloggning")
            void skaTaBortRememberMeCookienVidUtloggning() throws Exception {
                MvcResult inloggning = mockMvc.perform(post("/login")
                                .with(csrf())
                                .param("username", "testperson")
                                .param("password", "hemligt123")
                                .param("remember-me", "on"))
                        .andExpect(status().is3xxRedirection())
                        .andReturn();
                MockHttpSession session = (MockHttpSession) inloggning.getRequest().getSession(false);
                Cookie rememberMeCookie = inloggning.getResponse().getCookie("remember-me");

                mockMvc.perform(post("/logout").session(session).with(csrf()).cookie(rememberMeCookie))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrl("/login?logout"))
                        .andExpect(cookie().maxAge("remember-me", 0));
            }

            /**
             * WINE-40, granskningsfynd runda 2: pinnar fail-safe-grenen i
             * {@code SecurityConfig} (se dess klasskommentar) - saknas en
             * konfigurerad nyckel (produktionens lokala default är tom)
             * registreras remember-me-stödet inte alls i filterkedjan, så
             * kryssrutan ska bete sig som overksam (ingen cookie) istället
             * för att tyst signera med ett förutsägbart värde.
             *
             * Övriga tester i den här filen delar en gemensam kontext,
             * pinnad till `test-remember-me-nyckel` via
             * {@code @TestPropertySource} på klassnivå - det här enda
             * scenariot behöver tvärtom en TOM nyckel, vilket kräver en
             * egen Spring-kontext. {@code @NestedTestConfiguration(OVERRIDE)}
             * bryter arvet av den yttre klassens
             * {@code @TestPropertySource}, så kontext-annoteringarna nedan
             * måste upprepas i sin helhet.
             */
            @Nested
            @DisplayName("utan konfigurerad nyckel")
            @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
            @WebMvcTest(WineController.class)
            @Import(SecurityConfig.class)
            @TestPropertySource(properties = "winecellar.remember-me.key=")
            class UtanKonfigureradNyckel {

                @Autowired
                private MockMvc mockMvc;

                @Autowired
                private PasswordEncoder passwordEncoder;

                @MockBean
                private WineService wineService;

                @MockBean
                private LabelInterpretationService labelInterpretationService;

                @MockBean
                private UserRepository userRepository;

                @BeforeEach
                void stubbaTestanvändare() {
                    User testAnvändare = new User(
                            new UserId(1L), "testperson", passwordEncoder.encode("hemligt123"), Instant.now(), 0, false);
                    when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(testAnvändare));
                }

                @Test
                @DisplayName("ska INTE sätta någon remember-me-cookie, trots ikryssad ruta")
                void skaInteSättaRememberMeCookieNärNyckelSaknas() throws Exception {
                    MvcResult inloggning = mockMvc.perform(post("/login")
                                    .with(csrf())
                                    .param("username", "testperson")
                                    .param("password", "hemligt123")
                                    .param("remember-me", "on"))
                            .andExpect(status().is3xxRedirection())
                            .andExpect(redirectedUrl("/"))
                            .andReturn();

                    assertThat(inloggning.getResponse().getCookie("remember-me")).isNull();
                }
            }
        }
    }

    @Nested
    @DisplayName("startsidan")
    class Startsidan {

        /**
         * Källaren innehåller något som utgångsläge. Behövs sedan
         * verktygsraden (sök/sortering/filter) döljs helt i en TOM
         * källare - utan den här stubbningen returnerar listWines en tom
         * lista, sidan renderar sitt tomma läge, och varje test som letar
         * efter en kontroll i verktygsraden faller på något som egentligen
         * bara var en ofullständig stubbning. Enskilda test som vill testa
         * det tomma läget stubbar om metoden själva.
         */
        @BeforeEach
        void källarenInnehållerViner() {
            when(wineService.listWines(any())).thenReturn(List.of(BAROLO));
        }

        /**
         * De två tomma lägena är HELT olika situationer och får därför
         * inte visa samma text: en tom källare behöver en väg IN i appen,
         * ett sökresultat utan träffar en väg TILLBAKA till hela listan.
         */
        @Test
        @DisplayName("ska bjuda in till att lägga till det första vinet när källaren är tom")
        void skaVisaInbjudandeTomtLägeNärKällarenÄrTom() throws Exception {
            when(wineService.listWines(any())).thenReturn(List.of());
            when(wineService.search(any(), any())).thenReturn(List.of());

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Källaren är tom"),
                            containsString("href=\"/wines/nytt\""),
                            containsString("href=\"/import\""),
                            // Sök/sortering/filter är meningslösa utan data.
                            not(containsString("name=\"search\"")),
                            not(containsString("id=\"sort\""))
                    )));
        }

        /**
         * WINE-48, kodgranskningsfynd: den delade menyn i toppraden (som
         * WINE-48 ersatte den tidigare direkta kugghjulslänken med) hade
         * tidigare ingen testtäckning alls - varken här eller någon
         * annanstans. En flyttad/ombyggd navigeringslänk utan ett test är
         * en känd fälla (se CLAUDE.md) - ett brutet mvn verify hade inte
         * upptäckt att menyn tappat en av sina länkar.
         */
        @Test
        @DisplayName("ska visa menylänkar till chatten och inställningar i toppraden")
        void skaVisaMenylänkarTillChattenOchInställningarIToppraden() throws Exception {
            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("href=\"/chatt\""),
                            containsString("href=\"/installningar\"")
                    )));
        }

        @Test
        @DisplayName("ska erbjuda att rensa filtren när sökningen inte gav några träffar")
        void skaVisaIngaTräffarNärFiltretTömmerEnIckeTomKällare() throws Exception {
            when(wineService.listWines(any())).thenReturn(List.of(BAROLO));
            when(wineService.search(any(), any())).thenReturn(List.of());

            mockMvc.perform(get("/?search=finnsinte").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Inga viner matchar"),
                            not(containsString("Källaren är tom")),
                            // Verktygsraden MÅSTE finnas kvar här - annars
                            // går sökningen inte att ta bort igen.
                            containsString("name=\"search\"")
                    )));
        }

        /**
         * Simulerar EXAKT det läge sidan är i precis efter en redirect
         * från "Lägg till"/"Spara" - flashAttr() lägger värdet i samma
         * FlashMap-mekanism som RedirectAttributes.addFlashAttribute()
         * skriver till, som Spring sedan automatiskt speglar in i
         * Model:en för den här requesten. Utan det här testet bevisar
         * ingenting att mallens `${feedback}` faktiskt renderar värdet -
         * de två flash-testen ovan (i NärEttVinLäggsTill/NärEttVinRedigeras)
         * bevisar bara att controllern SÄTTER attributet, inte att GET /
         * visar det.
         */
        @Test
        @DisplayName("ska visa återkopplingsmeddelandet som kom med via en redirect")
        void skaVisaÅterkopplingsmeddelandeFrånRedirect() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf())
                            .flashAttr("feedback", "Vin tillagt"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Vin tillagt")));
        }

        @Test
        @DisplayName("ska lista befintliga viner och länka till formuläret för ett nytt vin")
        void skaListaBefintligaVinerOchLänkaTillNyttVinFormulär() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("href=\"/wines/nytt\""),
                            containsString("Barolo")
                    )));
        }

        @Test
        @DisplayName("ska rendera ett vin som bara har namnet ifyllt utan att krascha (typ/årgång/land/producent/plats null)")
        void skaRenderaEttVinMedBaraNamnetIfylltUtanAttKrascha() throws Exception {
            Wine minimaltVin = Wine.builder().id(new WineId(1L)).name("Chianti Classico").build();
            when(wineService.search(any(), any())).thenReturn(List.of(minimaltVin));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Chianti Classico")));
        }

        @Test
        @DisplayName("kortvyn (mobil) ska visa geografi- och betygsfält i översikten, resten infällt under \"Detaljer\"")
        void skaVisaFältUppdeladeMellanÖversiktOchDetaljer() throws Exception {
            Wine barolo = BAROLO.toBuilder()
                    .region("Piemonte").subregion("Langhe").grapes("Nebbiolo")
                    .purchaseDate(LocalDate.of(2024, 3, 15)).price(new BigDecimal("450.00"))
                    .purchaseReason("Rekommenderat").tastingNotes("Kraftfullt")
                    .ownRating(Rating.R16.label())
                    .systembolagetProductNumber("12345").systembolagetDescription("Beskrivning")
                    .munskankarnaReview("Recension").munskankarnaRating(Rating.R14_5)
                    .vivinoRating(new BigDecimal("4.1")).otherReference("https://example.com")
                    .build();
            when(wineService.search(any(), any())).thenReturn(List.of(barolo));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
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
            when(wineService.search(any(), any())).thenReturn(List.of(barolo));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
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
                    .ownRating(Rating.R16.label())
                    .build();
            when(wineService.search(any(), any())).thenReturn(List.of(barolo));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
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
                            // Redigera-ikonen ligger numera inne i Detaljer, inte i
                            // översikten - .detalj-atgarder delas mellan de breda korten
                            // och kortvyn. "Ta bort" flyttades till redigera-sidan
                            // (WINE-44), så den knappen finns inte längre här alls.
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
                    .ownRating(Rating.R16.label())
                    .systembolagetProductNumber("12345").systembolagetDescription("Beskrivning")
                    .munskankarnaReview("Recension").munskankarnaRating(Rating.R14_5)
                    .vivinoRating(new BigDecimal("4.1")).otherReference("https://example.com")
                    .build();
            when(wineService.search(any(), any())).thenReturn(List.of(barolo));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
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
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("name=\"sort\""),
                            containsString("name=\"direction\""),
                            containsString("value=\"NAME\""),
                            containsString("value=\"PRODUCER\""),
                            containsString("value=\"COUNTRY\""),
                            containsString("value=\"VINTAGE\""),
                            containsString("value=\"QUANTITY\""),
                            containsString("value=\"PRICE\""),
                            containsString("value=\"PURCHASE_DATE\""),
                            containsString("value=\"OWN_RATING\""),
                            containsString("value=\"MUNSKANKARNA_RATING\""),
                            containsString("value=\"VIVINO_RATING\""),
                            containsString("value=\"ASCENDING\""),
                            containsString("value=\"DESCENDING\"")
                    )));
        }

        @Test
        @DisplayName("ska sortera på namn, stigande, som standard när inget valts")
        void skaAnvändaStandardsortering() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk());

            verify(wineService).search(SearchCriteria.builder()
                    .sortField(SortField.NAME).sortDirection(SortDirection.ASCENDING)
                    .minQuantity(1)
                    .build(), null);
        }

        @Test
        @DisplayName("ska skicka valt sorteringsfält och riktning vidare till WineService")
        void skaSkickaValdSorteringTillWineService() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("sort", "OWN_RATING")
                            .param("direction", "DESCENDING"))
                    .andExpect(status().isOk());

            verify(wineService).search(SearchCriteria.builder()
                    .sortField(SortField.OWN_RATING).sortDirection(SortDirection.DESCENDING)
                    .minQuantity(1)
                    .build(), null);
        }

        @Test
        @DisplayName("ska returnera bara listfragmentet, inte hela sidan, vid en htmx-förfrågan")
        void skaReturneraBaraListfragmentetVidHtmxFörfrågan() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .header("HX-Request", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("id=\"vinlista\""),
                            containsString("Barolo"),
                            not(containsString("<html")),
                            not(containsString("name=\"sort\""))
                    )));
        }

        @Test
        @DisplayName("ska rendera kryssrutor för alla vintyper")
        void skaRenderaFilterkryssrutorMedAllaVintyper() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
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

        /**
         * WINE-51: taggfiltret är en flat kryssrutelista (till skillnad
         * från Ursprungs träd), härledd fräscht från samtliga av
         * användarens distinkta taggar - se WineService.distinctTags.
         */
        @Test
        @DisplayName("ska rendera en kryssruta per distinkt tagg i filterpanelen")
        void skaRenderaTaggkryssrutor() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));
            when(wineService.distinctTags(any())).thenReturn(List.of("Favorit", "Vardag"));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("name=\"tag\" value=\"Favorit\""),
                            containsString("name=\"tag\" value=\"Vardag\"")
                    )));
        }

        /**
         * Granskningsfynd/robusthet: en tom källare (inga taggar ännu)
         * ska inte visa en tom "Taggar"-sektion i filterpanelen - samma
         * princip som badgen bara räknar aktiva avvikelser, inte bara
         * existerande fält.
         */
        @Test
        @DisplayName("ska dölja taggsektionen i filterpanelen när ingen tagg finns ännu")
        void skaDöljaTaggsektionenUtanTaggar() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("name=\"tag\""))));
        }

        @Test
        @DisplayName("filterpanelens knapp ska heta \"Dölj filter\", inte \"Använd filter\" - checkrutorna applicerar redan filtret vid ändring")
        void skaHaKnappenDöljFilter() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Dölj filter"),
                            not(containsString("Använd filter"))
                    )));
        }

        @Test
        @DisplayName("ska rendera härkomstträdet som nästlade kryssrutor för land/region/underregion")
        void skaRenderaHärkomstträdetSomNästladeKryssrutor() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));
            when(wineService.originTree(any())).thenReturn(List.of(
                    new OriginNode("Italien", List.of(
                            new OriginNode("Piemonte", List.of(
                                    new OriginNode("Langhe", List.of())
                            ))
                    ))
            ));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
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
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));
            when(wineService.originTree(any())).thenReturn(List.of(
                    new OriginNode("Italien", List.of(
                            new OriginNode("Piemonte", List.of(
                                    new OriginNode("Langhe", List.of())
                            ))
                    )),
                    new OriginNode("Frankrike", List.of(
                            new OriginNode("Bourgogne", List.of())
                    ))
            ));

            String html = mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
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
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));
            when(wineService.originTree(any())).thenReturn(List.of(
                    new OriginNode("Italien", List.of(
                            new OriginNode("Piemonte", List.of(
                                    new OriginNode("Langhe", List.of())
                            ))
                    ))
            ));

            String html = mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).doesNotContain("<details open=\"open\">");
        }

        @Test
        @DisplayName("ska skicka valda filter vidare till WineService")
        void skaSkickaValdaFilterTillWineService() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("wineType", "RED", "WHITE")
                            .param("country", "Italien"))
                    .andExpect(status().isOk());

            verify(wineService).search(SearchCriteria.builder()
                    .sortField(SortField.NAME).sortDirection(SortDirection.ASCENDING)
                    .wineTypes(Set.of(WineType.RED, WineType.WHITE))
                    .countries(Set.of("Italien"))
                    .minQuantity(1)
                    .build(), null);
        }

        @Test
        @DisplayName("ska skicka valda taggar vidare till WineService")
        void skaSkickaValdaTaggarTillWineService() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("tag", "Favorit", "Vardag"))
                    .andExpect(status().isOk());

            verify(wineService).search(SearchCriteria.builder()
                    .sortField(SortField.NAME).sortDirection(SortDirection.ASCENDING)
                    .tags(Set.of("Favorit", "Vardag"))
                    .minQuantity(1)
                    .build(), null);
        }

        @Test
        @DisplayName("ska förhandskryssa redan valda filter vid sidladdning")
        void skaFörhandskryssaRedanValdaFilter() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("wineType", "RED"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            containsString("name=\"wineType\" value=\"RED\" checked")
                    ));
        }

        /**
         * WINE-41/WINE-42: `minQuantity` följer samma "explicit
         * queryparameter åsidosätter sparad default"-princip som
         * sort/direction redan gör - utan en förvald `userRepository`-
         * stubb (som i de flesta andra testerna här) faller den inloggade
         * "admin"-användaren tillbaka på 1, samma default som ett nytt
         * konto får (WINE-42 bytte defaulten från 0 till 1).
         */
        @Test
        @DisplayName("ska falla tillbaka på 1 för minQuantity när ingen queryparameter eller sparad default finns")
        void skaFallaTillbakaPåEttFörMinQuantity() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk());

            verify(wineService).search(SearchCriteria.builder()
                    .sortField(SortField.NAME).sortDirection(SortDirection.ASCENDING)
                    .minQuantity(1)
                    .build(), null);
        }

        @Test
        @DisplayName("ska använda användarens sparade standardval för minQuantity när ingen queryparameter finns")
        void skaAnvändaSparadDefaultFörMinQuantity() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));
            when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                    new User(new UserId(1L), "testperson", "hash", Instant.now(), 2, false)));

            mockMvc.perform(get("/").with(user("testperson")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"minQuantity\"")))
                    .andExpect(content().string(containsString("value=\"2\"")));

            verify(wineService).search(SearchCriteria.builder()
                    .sortField(SortField.NAME).sortDirection(SortDirection.ASCENDING)
                    .minQuantity(2)
                    .build(), new UserId(1L));
        }

        @Test
        @DisplayName("ska låta en explicit minQuantity-queryparameter åsidosätta den sparade defaulten")
        void skaLåtaExplicitMinQuantityÅsidosättaSparadDefault() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));
            when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                    new User(new UserId(1L), "testperson", "hash", Instant.now(), 2, false)));

            mockMvc.perform(get("/")
                            .with(user("testperson")).with(csrf())
                            .param("minQuantity", "5"))
                    .andExpect(status().isOk());

            verify(wineService).search(SearchCriteria.builder()
                    .sortField(SortField.NAME).sortDirection(SortDirection.ASCENDING)
                    .minQuantity(5)
                    .build(), new UserId(1L));
        }

        /**
         * Granskningsfynd (kodgranskning av PR #21): ett oparsbart
         * `minQuantity`-värde (trasig/manuellt redigerad länk) fick tidigare
         * `Integer.valueOf(...)` att kasta en ohanterad
         * `NumberFormatException` - ett 500-svar istället för att degradera
         * snyggt. Samma fallback-kedja som när parametern saknas helt: den
         * sparade defaulten, inte hårdkodad 0.
         */
        @Test
        @DisplayName("ska falla tillbaka på sparad default när minQuantity inte går att tolka som ett tal")
        void skaFallaTillbakaPåSparadDefaultFörOparsbartMinQuantity() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));
            when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                    new User(new UserId(1L), "testperson", "hash", Instant.now(), 2, false)));

            mockMvc.perform(get("/")
                            .with(user("testperson")).with(csrf())
                            .param("minQuantity", "abc"))
                    .andExpect(status().isOk());

            verify(wineService).search(SearchCriteria.builder()
                    .sortField(SortField.NAME).sortDirection(SortDirection.ASCENDING)
                    .minQuantity(2)
                    .build(), new UserId(1L));
        }

        /**
         * Badgen ska bara räkna minQuantity-filtret som "aktivt" när det
         * faktiska värdet AVVIKER från användarens sparade default -
         * annars hade badgen alltid visat minst 1 för varje inloggad
         * användare, även utan något aktivt val i den aktuella sessionen.
         */
        @Test
        @DisplayName("ska inte visa filterbadge när minQuantity bara motsvarar sparad default")
        void skaInteVisaBadgeNärMinQuantityMotsvararSparadDefault() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));
            when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                    new User(new UserId(1L), "testperson", "hash", Instant.now(), 2, false)));

            mockMvc.perform(get("/").with(user("testperson")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("class=\"antal-badge\""))));
        }

        @Test
        @DisplayName("ska visa filterbadge när minQuantity avviker från sparad default")
        void skaVisaBadgeNärMinQuantityAvvikerFrånSparadDefault() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));
            when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                    new User(new UserId(1L), "testperson", "hash", Instant.now(), 2, false)));

            mockMvc.perform(get("/")
                            .with(user("testperson")).with(csrf())
                            .param("minQuantity", "5"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("class=\"antal-badge\"")));
        }

        @Test
        @DisplayName("ska rendera sökfältet och skicka sökordet vidare till WineService")
        void skaSkickaSökordetTillWineService() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("search", "barolo"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            containsString("name=\"search\"")
                    ));

            verify(wineService).search(SearchCriteria.builder()
                    .searchTerm("barolo")
                    .sortField(SortField.NAME).sortDirection(SortDirection.ASCENDING)
                    .minQuantity(1)
                    .build(), null);
        }

        @Test
        @DisplayName("ska förhandsifylla sökfältet med det aktiva sökordet")
        void skaFörhandsifyllaSökfältet() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("search", "barolo"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            containsString("value=\"barolo\"")
                    ));
        }

        @Test
        @DisplayName("ska visa antal träffar av totalt antal viner")
        void skaVisaAntalTräffar() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));
            when(wineService.listWines(any())).thenReturn(List.of(BAROLO, BAROLO.toBuilder().name("Chablis").build()));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
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
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("class=\"chip\""))));
        }

        @Test
        @DisplayName("ska visa en chip per aktivt filter-/sökvärde, vars borttagningslänk behåller övriga värden")
        void skaVisaChipsMedBorttagningslänkar() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            String html = mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("search", "barolo")
                            .param("wineType", "RED")
                            .param("country", "Italien"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).contains("class=\"chip\"", "Sök: barolo ×", "Rött ×", "Italien ×");

            // "Rött"-chippens borttagningslänk ska ta bort wineType=RED,
            // men behålla sok och country oförändrade.
            java.util.regex.Matcher chipLänk = java.util.regex.Pattern
                    .compile("href=\"([^\"]+)\"[^>]*>Rött ×")
                    .matcher(html);
            assertThat(chipLänk.find()).isTrue();
            String href = chipLänk.group(1);
            assertThat(href).doesNotContain("wineType=RED")
                    .contains("country=Italien")
                    .contains("search=barolo");
        }

        /**
         * WINE-51: taggchippet i verktygsraden (ett aktivt filter) ska
         * vara en vanlig <a href>-länk, precis som övriga facettchips
         * (ADR 0008) - skild från vinkortens egna, icke-klickbara
         * taggchips (se skaVisaVinetsTaggarSomChipsPåKorten nedan).
         */
        @Test
        @DisplayName("ska visa en chip för ett aktivt taggfilter, med en borttagningslänk")
        void skaVisaTaggChipMedBorttagningslänk() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            String html = mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("tag", "Favorit"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).contains("Favorit ×");
            java.util.regex.Matcher chipLänk = java.util.regex.Pattern
                    .compile("href=\"([^\"]+)\"[^>]*>Favorit ×")
                    .matcher(html);
            assertThat(chipLänk.find()).isTrue();
            assertThat(chipLänk.group(1)).doesNotContain("tag=Favorit");
        }

        /**
         * WINE-51: taggar visas direkt på korten (bekräftat av
         * produktägaren), inte infällt under "Detaljer" - samma chip-stil
         * som filterchipsen (.chip i tema.css), men utan länk eftersom de
         * bara är informativa här, inte en handling.
         */
        @Test
        @DisplayName("ska visa vinets taggar som chips direkt på både breda och smala kort")
        void skaVisaVinetsTaggarSomChipsPåKorten() throws Exception {
            Wine barolo = BAROLO.toBuilder().tags(Set.of("Favorit", "Festvin")).build();
            when(wineService.search(any(), any())).thenReturn(List.of(barolo));

            String html = mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html)
                    .contains("class=\"chip\"")
                    .contains(">Favorit<")
                    .contains(">Festvin<");
        }

        @Test
        @DisplayName("sökchippet ska visa flera sökord ihopfogade med + istället för som en citerad fras, eftersom sökningen faktiskt är OCH mellan orden")
        void skaVisaFleraSökordMedPlusIChippet() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            String html = mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("search", "kraftfullt spanskt"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html)
                    .contains("Sök: kraftfullt + spanskt ×")
                    .doesNotContain("&quot;");
        }

        /**
         * WINE-44: "Redigera" ersattes av en ikonlänk (ingen synlig text
         * längre), och "Ta bort" finns inte kvar i vinlistan alls - se
         * "Farlig zon" i vin-formular.html/NärEttVinTasBort nedan för den
         * nya vägen.
         */
        @Test
        @DisplayName("ska rendera en Redigera-ikonlänk och inte längre någon \"Ta bort\"-knapp i vinlistan")
        void skaRenderaRedigeraIkonOchIngenTaBortKnapp() throws Exception {
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("aria-label=\"Redigera\""),
                            containsString("href=\"/wines/1/redigera\""),
                            // "hx-delete=" (med likhetstecken), inte bara "hx-delete" -
                            // ordet nämns fortfarande i en förklarande JS-kommentar
                            // längre upp i sidan (se vinkallare.html:s <head>).
                            not(containsString("hx-delete=")),
                            not(containsString(">Ta bort<"))
                    )));
        }
    }

    /**
     * WINE-55 ("Kom ihåg filtrering", se ADR 0023): filtreringen är
     * sessionsbunden, INTE databaspersisterad (till skillnad från
     * {@code minQuantity}s egen, oförändrade {@code defaultMinQuantityFilter}
     * - se {@code Startsidan} ovan, opåverkad av den här storyn). Varje
     * test som ska bevisa att något kommer ihåg kör TVÅ requester i SAMMA
     * {@link MockHttpSession} (fångad från den första requestens
     * {@link MvcResult}) - en enda request kan aldrig bevisa att något
     * faktiskt sparades.
     */
    @Nested
    @DisplayName("sessionsbunden filterminne (WINE-55)")
    class SessionsbundetFilterminne {

        @BeforeEach
        void källarenInnehållerViner() {
            when(wineService.listWines(any())).thenReturn(List.of(BAROLO));
            when(wineService.search(any(), any())).thenReturn(List.of(BAROLO));
        }

        private MockHttpSession sessionFrån(MvcResult result) {
            return (MockHttpSession) result.getRequest().getSession(false);
        }

        /**
         * Scenario 1: en explicit filtrering (verktygsradens formulär,
         * hx-trigger="change" - se vinkallare.html) ska fortfarande gälla
         * vid en EFTERFÖLJANDE, helt parameterlös navigering till
         * startsidan i samma session - motsvarar att ha besökt en annan
         * sida (t.ex. Inställningar) och gått tillbaka.
         */
        @Test
        @DisplayName("ska komma ihåg sortering/filtrering/sökning vid en efterföljande bar navigering i samma session")
        void skaKommaIhågFiltreringVidEfterföljandeBarNavigering() throws Exception {
            MvcResult förstaRequesten = mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("search", "barolo")
                            .param("sort", "PRICE").param("direction", "DESCENDING")
                            .param("wineType", "RED"))
                    .andExpect(status().isOk())
                    .andReturn();

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf())
                            .session(sessionFrån(förstaRequesten)))
                    .andExpect(status().isOk());

            // Anropas EN gång per request (första + den bara uppföljaren) -
            // exakt samma kriterier bägge gångerna är själva poängen med
            // att minnas, så times(2) är rätt förväntan, inte times(1).
            verify(wineService, times(2)).search(SearchCriteria.builder()
                    .searchTerm("barolo")
                    .sortField(SortField.PRICE).sortDirection(SortDirection.DESCENDING)
                    .wineTypes(Set.of(WineType.RED))
                    .minQuantity(1)
                    .build(), null);
        }

        /**
         * Scenario 2: samma sak efter en PLAIN "redirect:/" (exakt det
         * `deleteWine`/`saveEdit`/`addWine` redan gör) - bevisar att den
         * ihågkomna filtreringen inte var beroende av NÅGOT annat i den
         * första requestens URL än att den var parameterlös, vilket en
         * riktig redirect-respons också är.
         */
        @Test
        @DisplayName("ska komma ihåg filtreringen efter en redirect till en helt parameterlös / (efter tillägg/redigering/borttagning)")
        void skaKommaIhågFiltreringEfterRedirectTillPlainRot() throws Exception {
            MvcResult förstaRequesten = mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("sort", "VINTAGE").param("direction", "DESCENDING")
                            .param("country", "Italien"))
                    .andExpect(status().isOk())
                    .andReturn();
            MockHttpSession session = sessionFrån(förstaRequesten);

            // Exakt den navigering en redirect:/ (efter t.ex. saveEdit) resulterar i.
            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()).session(session))
                    .andExpect(status().isOk());

            verify(wineService, times(2)).search(SearchCriteria.builder()
                    .sortField(SortField.VINTAGE).sortDirection(SortDirection.DESCENDING)
                    .countries(Set.of("Italien"))
                    .minQuantity(1)
                    .build(), null);
        }

        /**
         * Scenario 3: "Rensa filter"/"Rensa sökning och filter" (nu
         * `/?reset=true`, se vinkallare.html) måste faktiskt TÖMMA
         * sessionens minne - annars hade länken bara återställt exakt
         * samma filter den skulle ta bort, eftersom en bar `/` annars
         * tolkas som "använd det ihågkomna filtret".
         */
        @Test
        @DisplayName("reset=true ska nollställa det ihågkomna filtret, så en efterföljande bar navigering inte återställer det gamla")
        void skaNollställaIhågkommetFilterVidReset() throws Exception {
            MvcResult förstaRequesten = mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("sort", "PRICE").param("direction", "DESCENDING")
                            .param("wineType", "RED"))
                    .andExpect(status().isOk())
                    .andReturn();
            MockHttpSession session = sessionFrån(förstaRequesten);

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf())
                            .session(session).param("reset", "true"))
                    .andExpect(status().isOk());

            // En TREDJE, helt bar request (ingen reset-parameter denna gång)
            // ska nu visa vanliga defaults, INTE det ursprungliga PRICE/RED-filtret.
            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()).session(session))
                    .andExpect(status().isOk());

            verify(wineService, times(2)).search(SearchCriteria.builder()
                    .sortField(SortField.NAME).sortDirection(SortDirection.ASCENDING)
                    .minQuantity(1)
                    .build(), null);
        }

        /**
         * Scenario 4: en explicit queryparameter (t.ex. en delad/bokmärkt
         * länk) åsidosätter alltid det ihågkomna värdet för SITT EGET fält,
         * och blir självt det nya ihågkomna värdet - övriga, orörda fält
         * (här: wineType/sort/direction) hämtas fortfarande ur minnet,
         * samma princip som `minQuantity` redan följer. Den andra requesten
         * har medvetet VARKEN sort- eller direction-parameter, så den
         * hamnar i fält-för-fält-grenen av resolveFilter (inte
         * "hela-verktygsraden"-grenen som de andra testen här utlöser via
         * sort/direction).
         */
        @Test
        @DisplayName("en explicit queryparameter ska åsidosätta bara sitt eget ihågkomna fält, och bli det nya ihågkomna värdet")
        void skaLåtaExplicitParameterÅsidosättaBaraSittEgetFält() throws Exception {
            MvcResult förstaRequesten = mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("search", "första")
                            .param("sort", "PRICE").param("direction", "DESCENDING")
                            .param("wineType", "RED"))
                    .andExpect(status().isOk())
                    .andReturn();
            MockHttpSession session = sessionFrån(förstaRequesten);

            // En bokmärkt/delad länk som bara anger ett nytt sökord.
            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf())
                            .session(session).param("search", "andra"))
                    .andExpect(status().isOk());

            verify(wineService).search(SearchCriteria.builder()
                    .searchTerm("andra")
                    .sortField(SortField.PRICE).sortDirection(SortDirection.DESCENDING)
                    .wineTypes(Set.of(WineType.RED))
                    .minQuantity(1)
                    .build(), null);

            // Det nya sökordet ska i sin tur ha blivit det ihågkomna värdet -
            // en TREDJE, bar request (utan sökparameter) ska fortfarande visa "andra".
            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()).session(session))
                    .andExpect(status().isOk());

            // times(2): den andra OCH den tredje requesten gav samma kriterier.
            verify(wineService, times(2)).search(SearchCriteria.builder()
                    .searchTerm("andra")
                    .sortField(SortField.PRICE).sortDirection(SortDirection.DESCENDING)
                    .wineTypes(Set.of(WineType.RED))
                    .minQuantity(1)
                    .build(), null);
        }

        /**
         * Scenario 5: en NY session (motsvarar en ny inloggning) ska
         * fortfarande visa vanliga defaults, oavsett vad en TIDIGARE,
         * separat session hade ihågkommet - filtreringen är alltså
         * medvetet inte databaspersisterad per användare (se ADR 0023).
         * De två requesterna delar HÄR ingen session alls (ingen
         * `.session(...)` anropas) - varje `mockMvc.perform` utan en
         * explicit session får sin egen, tomma {@link MockHttpSession}.
         */
        @Test
        @DisplayName("en ny session ska starta om från vanliga defaults, oavsett vad en tidigare session hade ihågkommet")
        void skaStartaOmFrånDefaultsIEnNySession() throws Exception {
            mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("sort", "PRICE").param("direction", "DESCENDING")
                            .param("wineType", "RED"))
                    .andExpect(status().isOk());

            // Ingen delad session - en helt fristående request, som en ny inloggning.
            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk());

            verify(wineService).search(SearchCriteria.builder()
                    .sortField(SortField.NAME).sortDirection(SortDirection.ASCENDING)
                    .minQuantity(1)
                    .build(), null);
        }

        /**
         * Scenario 6 (granskningsfynd, WINE-55): {@code sort} satt UTAN
         * {@code direction} ska INTE tolkas som en fullständig
         * verktygsradsbeskrivning (den grenen kräver BÅDA fälten, se
         * {@link WineController#resolveFilter}) - requesten hamnar i
         * stället i fält-för-fält-grenen, som en vanlig explicit
         * queryparameter. Övriga redan ihågkomna fält (här: wineType) ska
         * alltså BEVARAS, inte tappas bort som vid en total-ersättning.
         */
        @Test
        @DisplayName("bara sort satt (ingen direction) ska bara uppdatera sorteringsfältet och bevara övriga ihågkomna facetter")
        void skaBaraUppdateraSortNärDirectionSaknasOchBevaraÖvrigaIhågkomnaFacetter() throws Exception {
            MvcResult förstaRequesten = mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("sort", "PRICE").param("direction", "DESCENDING")
                            .param("wineType", "RED"))
                    .andExpect(status().isOk())
                    .andReturn();
            MockHttpSession session = sessionFrån(förstaRequesten);

            // Bara sort satt - VARKEN en hel verktygsradsrequest (som kräver
            // BÅDA fälten) eller en bar navigering.
            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf())
                            .session(session).param("sort", "NAME"))
                    .andExpect(status().isOk());

            // direction och wineType hämtas ur minnet - PARTIELL uppdatering,
            // inte en total-ersättning (som hade tappat bort wineType=RED
            // eftersom den andra requesten inte skickar den parametern alls).
            verify(wineService).search(SearchCriteria.builder()
                    .sortField(SortField.NAME).sortDirection(SortDirection.DESCENDING)
                    .wineTypes(Set.of(WineType.RED))
                    .minQuantity(1)
                    .build(), null);
        }

        /**
         * Scenario 7 (granskningsfynd, WINE-55): bevisar den faktiska
         * motiveringen för att en FULLSTÄNDIG verktygsradsrequest (BÅDA
         * sort och direction satta) ska ERSÄTTA hela det ihågkomna
         * filtret i stället för att slås ihop - en avmarkerad sista
         * kryssrutan i en facett (här: wineType) måste faktiskt kunna
         * försvinna ur minnet, inte tyst återställas av en efterföljande
         * bar navigering.
         */
        @Test
        @DisplayName("en andra fullständig verktygsradsrequest med färre valda facetter ska ersätta - inte slå ihop med - den första")
        void skaErsättaHelaDetIhågkomnaFiltretVidEnAndraFullständigVerktygsradsrequest() throws Exception {
            MvcResult förstaRequesten = mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("sort", "NAME").param("direction", "ASCENDING")
                            .param("wineType", "RED"))
                    .andExpect(status().isOk())
                    .andReturn();
            MockHttpSession session = sessionFrån(förstaRequesten);

            // Andra, fullständiga verktygsradsrequesten - wineType-kryssrutan
            // har nu avmarkerats helt (ingen wineType-parameter alls).
            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf())
                            .session(session)
                            .param("sort", "NAME").param("direction", "ASCENDING"))
                    .andExpect(status().isOk());

            // En TREDJE, helt bar navigering - wineType=RED ska INTE längre
            // vara ihågkommet.
            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf()).session(session))
                    .andExpect(status().isOk());

            verify(wineService, times(2)).search(SearchCriteria.builder()
                    .sortField(SortField.NAME).sortDirection(SortDirection.ASCENDING)
                    .minQuantity(1)
                    .build(), null);
        }

        /**
         * Scenario 8 (WINE-56, se docs/adr/0024-chat-wine-mention-links.md):
         * AI-chattens "visa dessa viner i vinlistan"-länk kombinerar
         * {@code reset=true} med en uppsättning {@code name}-parametrar i
         * SAMMA request - granskningsfynd från WINE-55 som gjorde att
         * {@code reset=true} tidigare ignorerade övriga queryparametrar på
         * samma request helt (returnerade rakt av
         * {@code RememberedFilter.defaults()}). Den kombinationen måste
         * visa EXAKT de nämnda vinerna, oavsett vad som var aktivt
         * filtrerat sedan innan.
         */
        @Test
        @DisplayName("reset=true kombinerat med name-parametrar i samma request ska visa exakt de namngivna vinerna, oavsett tidigare aktivt filter")
        void skaKombineraResetMedNameParametrarISammaRequest() throws Exception {
            MvcResult förstaRequesten = mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("sort", "PRICE").param("direction", "DESCENDING")
                            .param("wineType", "RED"))
                    .andExpect(status().isOk())
                    .andReturn();
            MockHttpSession session = sessionFrån(förstaRequesten);

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf())
                            .session(session)
                            .param("reset", "true")
                            .param("name", "Barolo").param("name", "Chablis"))
                    .andExpect(status().isOk());

            verify(wineService).search(SearchCriteria.builder()
                    .sortField(SortField.NAME).sortDirection(SortDirection.ASCENDING)
                    .names(Set.of("Barolo", "Chablis"))
                    .minQuantity(1)
                    .build(), null);
        }

        /**
         * Scenario 11 (regressionstest, WINE-56): de BEFINTLIGA "Rensa
         * filter"/"Rensa sökning och filter"-länkarna (en bar
         * {@code /?reset=true}, utan några andra parametrar) ska fortsätta
         * bete sig EXAKT som innan denna story - en fullständig
         * nollställning, ingen kvarvarande facett. Bevisar att fixen ovan
         * (reset=true tillämpar övriga explicita parametrar) inte råkade
         * ändra beteendet när det INTE finns några andra parametrar att
         * tillämpa.
         */
        @Test
        @DisplayName("reset=true utan några andra parametrar ska fortfarande nollställa filtret helt (regressionstest)")
        void skaFortfarandeNollställaHeltNärResetSaknarÖvrigaParametrar() throws Exception {
            MvcResult förstaRequesten = mockMvc.perform(get("/")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("search", "barolo")
                            .param("sort", "PRICE").param("direction", "DESCENDING")
                            .param("wineType", "RED").param("tag", "Favorit"))
                    .andExpect(status().isOk())
                    .andReturn();
            MockHttpSession session = sessionFrån(förstaRequesten);

            mockMvc.perform(get("/").with(user("admin").roles("ADMIN")).with(csrf())
                            .session(session).param("reset", "true"))
                    .andExpect(status().isOk());

            verify(wineService).search(SearchCriteria.builder()
                    .sortField(SortField.NAME).sortDirection(SortDirection.ASCENDING)
                    .minQuantity(1)
                    .build(), null);
        }
    }

    @Nested
    @DisplayName("när formuläret för ett nytt vin visas")
    class NärFormuläretFörEttNyttVinVisas {

        @Test
        @DisplayName("ska formuläret vara tomt")
        void skaFormuläretVaraTomt() throws Exception {
            mockMvc.perform(get("/wines/nytt").with(user("admin").roles("ADMIN")).with(csrf()))
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

        /**
         * WINE-51/WINE-52: förslagen (den inloggade användarens redan
         * använda, distinkta taggar - även vid TILLÄGG, inte bara
         * redigering) renderas serversidan som ett vanligt HTML
         * &lt;datalist&gt;, precis som från början (WINE-51). Sedan
         * WINE-52 är &lt;datalist&gt; bara en datakälla för sidans EGNA
         * JS-dropdown (se vin-formular.html) - <datalist>/list="..."-
         * kopplingen visade sig inte fungera i mobila webbläsare (iOS
         * Safari saknar helt stöd, Android Chrome har historiskt
         * inkonsekvent stöd), så inputen har därför inte längre ett
         * list-attribut. Testet verifierar bara att datakällan finns med
         * rätt värden - själva JS-dropdownens beteende kräver en riktig
         * webbläsare, se VinFormularIT.
         */
        @Test
        @DisplayName("ska rendera taggfältet med autocomplete-förslag från tidigare använda taggar")
        void skaRenderaTaggfältetMedFörslag() throws Exception {
            when(wineService.distinctTags(any())).thenReturn(List.of("Favorit", "Vardag"));

            mockMvc.perform(get("/wines/nytt").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("id=\"tagg-input\""),
                            not(containsString("list=\"tagg-forslag\"")),
                            containsString("id=\"tagg-forslag\""),
                            containsString("value=\"Favorit\""),
                            containsString("value=\"Vardag\"")
                    )));
        }

        /**
         * WINE-44: det finns inget att radera förrän vinet är sparat -
         * knappen/dialogen ska inte visas alls på "lägg till"-formuläret.
         */
        @Test
        @DisplayName("ska inte visa någon \"Radera vinet\"-knapp")
        void skaInteVisaRaderaKnapp() throws Exception {
            // Kollar den faktiska knappens id (en HTML-attribut-sträng),
            // inte fritext som "Radera vinet"/"<dialog" - båda förekommer
            // ordagrant i förklarande kommentarer längre ner i samma sida
            // (JS-kommentaren vid dialoglogiken, se vin-formular.html),
            // vilket hade gett falska träffar.
            mockMvc.perform(get("/wines/nytt").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("id=\"oppna-radera-dialog\""))));
        }
    }

    /**
     * WINE-50: vin-formular.html visar "Eget betyg" antingen som ett
     * fritextfält eller som en dropdown med munskänkarnas 29 etiketter,
     * beroende på den inloggade användarens sparade inställning
     * (User.ownRatingFromScale) - default (ingen stubbad användare i
     * userRepository-mocken, samma som en oregistrerad/okänd inloggning)
     * är fritext, precis som ett nytt konto får (se RegistrationService).
     * "Munskänkarnas betyg" är oförändrat alltid en dropdown, oavsett
     * inställningen - testas inte här (redan täckt av övriga tester i den
     * här klassen).
     */
    @Nested
    @DisplayName("Eget betyg - fritext kontra dropdown beroende på kontots inställning")
    class EgetBetygFritextKontraDropdown {

        @Test
        @DisplayName("ska visa ett fritextfält som standard (ingen sparad inställning)")
        void skaVisaFritextfältSomStandard() throws Exception {
            mockMvc.perform(get("/wines/nytt").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("<input type=\"text\" name=\"ownRating\""),
                            not(containsString("<select name=\"ownRating\""))
                    )));
        }

        @Test
        @DisplayName("ska visa en dropdown med munskänkarnas etiketter när inställningen är påslagen")
        void skaVisaDropdownNärInställningenÄrPåslagen() throws Exception {
            when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                    new User(new UserId(1L), "testperson", "hash", Instant.now(), 1, true)));

            mockMvc.perform(get("/wines/nytt").with(user("testperson")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("<select name=\"ownRating\""),
                            not(containsString("<input type=\"text\" name=\"ownRating\"")),
                            // Alternativets VÄRDE är etiketten själv, inte Rating-
                            // konstantens korta namn - annars hade "R16" sparats
                            // rakt av i stället för den fulla texten (se
                            // WineController.applyFormFields). Kan INTE kolla
                            // frånvaro av "value=\"R16\"" i hela sidan - den
                            // oförändrade munskankarnaRating-dropdownen använder
                            // fortfarande just Rating-konstantens korta namn som
                            // värde, helt legitimt för det fältet.
                            containsString("value=\"" + Rating.R16.label() + "\"")
                    )));
        }

        @Test
        @DisplayName("ska förvälja det sparade värdet i dropdownen när det matchar en av munskänkarnas etiketter")
        void skaFörväljaSparatVärdeIDropdown() throws Exception {
            when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                    new User(new UserId(1L), "testperson", "hash", Instant.now(), 1, true)));
            when(wineService.findById(eq(new WineId(1L)), any()))
                    .thenReturn(Optional.of(BAROLO.toBuilder().ownRating(Rating.R16.label()).build()));

            mockMvc.perform(get("/wines/1/redigera").with(user("testperson")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            containsString("value=\"" + Rating.R16.label() + "\" selected")));
        }

        @Test
        @DisplayName("ska visa det sparade fritextvärdet i textfältet när inställningen är avslagen")
        void skaVisaSparatFritextvärdeITextfältet() throws Exception {
            when(wineService.findById(eq(new WineId(1L)), any()))
                    .thenReturn(Optional.of(BAROLO.toBuilder().ownRating("Riktigt bra, dricka nu").build()));

            mockMvc.perform(get("/wines/1/redigera").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            containsString("<input type=\"text\" name=\"ownRating\" value=\"Riktigt bra, dricka nu\"")));
        }

        /**
         * Granskningsfynd (kodgranskning av PR #32): ett vin kan ha ett
         * sparat "eget betyg" som INTE matchar någon av munskänkarnas 29
         * etiketter (satt i fritextläge, eller innan inställningen fanns).
         * Utan ett extra, förvalt alternativ i dropdownen för just det här
         * fallet hade den tyst fallit tillbaka till "Inget betyg" - och en
         * oförändrad sparning (användaren rör aldrig fältet) hade då tyst
         * nollat ut det gamla värdet, se skaBevaraOmatchatFritextvärdeVidOförändradSparning
         * nedan för den delen av regressionen.
         */
        @Test
        @DisplayName("ska visa ett omatchat sparat värde som ett extra, förvalt alternativ i dropdownen")
        void skaVisaOmatchatSparatVärdeSomExtraFörvaltAlternativ() throws Exception {
            when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                    new User(new UserId(1L), "testperson", "hash", Instant.now(), 1, true)));
            when(wineService.findById(eq(new WineId(1L)), any()))
                    .thenReturn(Optional.of(BAROLO.toBuilder().ownRating("Fantastiskt, dricka nu!").build()));

            mockMvc.perform(get("/wines/1/redigera").with(user("testperson")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            containsString("<option value=\"Fantastiskt, dricka nu!\" "
                                    + "selected=\"selected\">Fantastiskt, dricka nu!</option>")));
        }

        /**
         * Regressionstest för samma granskningsfynd som ovan: en oförändrad
         * sparning (bara priset ändrat, betygsfältet aldrig rört - simulerat
         * genom att posta exakt det värde det extra dropdown-alternativet
         * ovan förvalde) ska bevara det ursprungliga fritextvärdet, inte
         * nolla ut det.
         */
        @Test
        @DisplayName("ska bevara ett omatchat fritextvärde vid en oförändrad sparning i dropdown-läge")
        void skaBevaraOmatchatFritextvärdeVidOförändradSparning() throws Exception {
            Wine existing = BAROLO.toBuilder().ownRating("Fantastiskt, dricka nu!").build();
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.of(existing));

            mockMvc.perform(post("/wines/1/redigera")
                            .with(user("testperson")).with(csrf())
                            .param("name", "Barolo")
                            .param("wineType", "RED")
                            .param("producer", "Pio Cesare")
                            .param("country", "Italien")
                            .param("vintage", "2018")
                            .param("quantity", "3")
                            .param("location", "Låda 1")
                            .param("price", "199.00")
                            .param("ownRating", "Fantastiskt, dricka nu!"))
                    .andExpect(status().is3xxRedirection());

            verify(wineService).save(existing.toBuilder()
                    .price(new BigDecimal("199.00"))
                    .ownRating("Fantastiskt, dricka nu!")
                    .build());
        }
    }

    @Nested
    @DisplayName("när en etikett skannas")
    class NärEnEtikettSkannas {

        @Test
        @DisplayName("ska visa ett förifyllt utkast med de tolkade fälten markerade och ett statusmeddelande om vad som fylldes i")
        void skaVisaEttFörifylltUtkastMedDeTolkadeFältenMarkerade() throws Exception {
            Wine draft = Wine.builder().name("Barolo").producer("Pio Cesare").vintage(2018)
                    .country("Italien").region("Piemonte").build();
            when(labelInterpretationService.interpret(any(), any())).thenReturn(
                    new LabelInterpretationResult.Interpreted(draft, Set.of("name", "producer", "vintage", "country", "region")));

            mockMvc.perform(multipart("/wines/tolka-etikett")
                            .file(new MockMultipartFile("bild", "etikett.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                            .with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("value=\"Barolo\""),
                            containsString("value=\"Pio Cesare\""),
                            containsString("value=\"2018\""),
                            containsString("value=\"Italien\""),
                            containsString("value=\"Piemonte\""),
                            containsString("class=\"tolkat-falt\""),
                            containsString("Fyllde i: Namn, Producent, Årgång, Land, Region")
                    )));
        }

        @Test
        @DisplayName("ska lista bara de faktiskt tolkade fälten i statusmeddelandet, i fast ordning")
        void skaListaBaraDeFaktisktTolkadeFältenIStatusmeddelandet() throws Exception {
            Wine draft = Wine.builder().name("Chablis").build();
            when(labelInterpretationService.interpret(any(), any())).thenReturn(
                    new LabelInterpretationResult.Interpreted(draft, Set.of("name")));

            mockMvc.perform(multipart("/wines/tolka-etikett")
                            .file(new MockMultipartFile("bild", "etikett.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                            .with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Fyllde i: Namn.")));
        }

        @Test
        @DisplayName("ska visa ett felmeddelande och ett tomt formulär om tolkningen misslyckas")
        void skaVisaFelmeddelandeOchTomtFormulärOmTolkningenMisslyckas() throws Exception {
            when(labelInterpretationService.interpret(any(), any())).thenReturn(new LabelInterpretationResult.Failed());

            mockMvc.perform(multipart("/wines/tolka-etikett")
                            .file(new MockMultipartFile("bild", "etikett.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                            .with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Det gick inte att tolka etiketten"),
                            not(containsString("class=\"tolkat-falt\"")),
                            not(containsString("Fyllde i:"))
                    )));
        }

        @Test
        @DisplayName("ska rendera statusraden för \"analyserar\" i formuläret för ett nytt vin")
        void skaRenderaStatusradenFörAnalyserar() throws Exception {
            mockMvc.perform(get("/wines/nytt").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"etikett-status\"")));
        }

        @Test
        @DisplayName("ska nekas utan inloggning och aldrig nå LabelInterpretationService")
        void skaNekasUtanInloggning() throws Exception {
            mockMvc.perform(multipart("/wines/tolka-etikett")
                            .file(new MockMultipartFile("bild", "etikett.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));

            verify(labelInterpretationService, never()).interpret(any(), any());
        }
    }

    @Nested
    @DisplayName("när ett vin läggs till")
    class NärEttVinLäggsTill {

        @Test
        @DisplayName("ska vinet skickas till WineService och sidan omdirigera till startsidan")
        void skaSkickasTillServiceOchOmdirigera() throws Exception {
            mockMvc.perform(post("/wines")
                            .with(user("admin").roles("ADMIN")).with(csrf())
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

        /**
         * Täckningsglapp hittat av användaren efter granskningen av PR #32:
         * "Eget betyg"s fritext-/dropdown-läge hade bara tester för
         * REDIGERING (se EgetBetygFritextKontraDropdown-klassen och
         * NärEttVinRedigeras nedan), inget för TILLÄGG - trots att båda
         * flödena delar samma applyFormFields-metod. Default (ingen stubbad
         * användare i userRepository-mocken) är fritext, samma default som
         * ett nytt konto får (se RegistrationService).
         */
        @Test
        @DisplayName("ska spara eget betyg som fri text när formuläret postas i fritextläge (default)")
        void skaSparaEgetBetygSomFriTextVidTillägg() throws Exception {
            mockMvc.perform(post("/wines")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("name", "Barolo")
                            .param("quantity", "3")
                            .param("ownRating", "Riktigt gott, testa igen om ett år"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));

            verify(wineService).save(Wine.builder()
                    .name("Barolo").quantity(3)
                    .ownRating("Riktigt gott, testa igen om ett år")
                    .build());
        }

        /**
         * Bevisar att tillägg och redigering beter sig identiskt (delad
         * applyFormFields), inte bara antar det: när kontot har
         * dropdown-läge påslaget och formuläret postar en av munskänkarnas
         * etiketter (som om användaren valt den i dropdownen) ska
         * ETIKETT-STRÄNGEN sparas rakt av - aldrig en Rating-konstant.
         */
        @Test
        @DisplayName("ska spara eget betyg som etikett-text (inte en Rating-konstant) när dropdown-läget är påslaget")
        void skaSparaEgetBetygSomEtikettTextVidTilläggMedDropdownPåslagen() throws Exception {
            when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                    new User(new UserId(1L), "testperson", "hash", Instant.now(), 1, true)));

            mockMvc.perform(post("/wines")
                            .with(user("testperson")).with(csrf())
                            .param("name", "Barolo")
                            .param("quantity", "3")
                            .param("ownRating", Rating.R16.label()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));

            verify(wineService).save(Wine.builder()
                    .name("Barolo").quantity(3)
                    .owner(new UserId(1L))
                    .ownRating(Rating.R16.label())
                    .build());
        }

        /**
         * Måste vara ett FLASH-attribut, inte ett vanligt model-attribut:
         * metoden redirectar till GET /, en helt ny request som inte ser
         * det ursprungliga anropets Model. addFlashAttribute sparar värdet
         * över redirecten (en request) och Spring lägger automatiskt in
         * det i Model:en för nästa request - det är den mekanism
         * vinkallare.html:s `${feedback}` förlitar sig på.
         */
        @Test
        @DisplayName("ska sätta ett flash-meddelande som visas efter omdirigeringen")
        void skaSättaFlashMeddelande() throws Exception {
            mockMvc.perform(post("/wines")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("name", "Barolo")
                            .param("quantity", "3"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("feedback", "Vin tillagt"));
        }

        @Test
        @DisplayName("ska gå att lägga till ett vin med bara namnet ifyllt - antal faller tillbaka till 1, övriga fält blir null")
        void skaGåAttLäggaTillMedBaraNamnet() throws Exception {
            mockMvc.perform(post("/wines")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("name", "Chianti Classico"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));

            verify(wineService).save(Wine.builder()
                    .name("Chianti Classico")
                    .quantity(1)
                    .build());
        }

        /**
         * WINE-51: taggar binds direkt till Set<String> (samma mönster som
         * filterpanelens wineType/country-kryssrutor) - en kryssruta per
         * tagg (name="tags"), inte ett kommaseparerat textfält. Ett
         * whitespace-bara "smyg"-värde (kan inte uppstå via UI:t, men
         * ofarligt att skydda mot ändå) filtreras bort av normalizeTags.
         */
        @Test
        @DisplayName("ska spara taggarna som skickades med formuläret")
        void skaSparaTaggarna() throws Exception {
            mockMvc.perform(post("/wines")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("name", "Barolo")
                            .param("quantity", "3")
                            .param("tags", "Favorit", "Festvin", "  "))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));

            verify(wineService).save(Wine.builder()
                    .name("Barolo").quantity(3)
                    .tags(Set.of("Favorit", "Festvin"))
                    .build());
        }

        @Test
        @DisplayName("ska bilden sparas tillsammans med resten av vinet om en fil valdes")
        void skaBildenSparasTillsammansMedResten() throws Exception {
            byte[] bilddata = new byte[]{1, 2, 3};

            mockMvc.perform(multipart("/wines")
                            .file(new MockMultipartFile("bild", "etikett.jpg", "image/jpeg", bilddata))
                            .with(user("admin").roles("ADMIN")).with(csrf())
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

        @Test
        @DisplayName("ska varna om vinet är en fullständig dubblett och inte spara det")
        void skaVarnaOmFullständigDubblettOchInteSpara() throws Exception {
            Wine existing = Wine.builder()
                    .id(new WineId(1L)).name("Barolo").producer("Pio Cesare").vintage(2018).quantity(3)
                    .build();
            when(wineService.checkForDuplicate(any(), any())).thenReturn(new DuplicateCheck.FullDuplicate(existing));

            mockMvc.perform(post("/wines")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("name", "Barolo")
                            .param("producer", "Pio Cesare")
                            .param("vintage", "2018"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Det här vinet finns redan"),
                            not(containsString("Lägg till som nytt vin ändå")),
                            containsString("dubblett-oka-antal")
                    )));

            verify(wineService, never()).save(any());
        }

        @Test
        @DisplayName("ska varna om vinet är en möjlig dubblett, inte spara det, men erbjuda att lägga till ändå")
        void skaVarnaOmMöjligDubblettOchErbjudaAttLäggaTillÄndå() throws Exception {
            Wine existing = Wine.builder()
                    .id(new WineId(1L)).name("Barolo").producer("Pio Cesare").vintage(2018).quantity(3)
                    .build();
            when(wineService.checkForDuplicate(any(), any())).thenReturn(new DuplicateCheck.PartialDuplicate(existing));

            mockMvc.perform(post("/wines")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("name", "Barolo"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Det finns redan ett vin som liknar det här"),
                            containsString("name=\"confirmAdd\""),
                            containsString("dubblett-oka-antal")
                    )));

            verify(wineService, never()).save(any());
        }

        @Test
        @DisplayName("ska spara vinet ändå om confirmAdd är satt, utan att fråga WineService om det är en dubblett")
        void skaSparaÄndåOmConfirmAddÄrSatt() throws Exception {
            mockMvc.perform(post("/wines")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("name", "Barolo")
                            .param("confirmAdd", "true"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));

            verify(wineService, never()).checkForDuplicate(any(), any());
            verify(wineService).save(Wine.builder().name("Barolo").quantity(1).build());
        }
    }

    @Nested
    @DisplayName("dubblettvarningens \"öka antal\"-val")
    class DubblettvarningensÖkaAntalVal {

        @Test
        @DisplayName("ska öka antalet på det befintliga vinet och omdirigera till startsidan")
        void skaÖkaAntaletOchOmdirigera() throws Exception {
            mockMvc.perform(post("/wines/1/dubblett-oka-antal").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));

            verify(wineService).increaseQuantity(new WineId(1L), null);
        }

        @Test
        @DisplayName("ska nekas utan inloggning och aldrig nå WineService")
        void skaNekasUtanInloggning() throws Exception {
            mockMvc.perform(post("/wines/1/dubblett-oka-antal").with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));

            verify(wineService, never()).increaseQuantity(any(), any());
        }
    }

    /**
     * WINE-44: raderingen flyttades från vinlistans htmx-baserade
     * DELETE-knapp till en bekräftad radering på redigera-sidan - se
     * "Farlig zon" i vin-formular.html. Samma POST+redirect-mönster som
     * lägg till/redigera nu, i stället för den gamla direkta
     * fragment-renderingen (se devlog/CLAUDE.md för den tidigare
     * mekanismen).
     */
    @Nested
    @DisplayName("när ett vin raderas")
    class NärEttVinRaderas {

        @Test
        @DisplayName("ska id och ägare skickas till WineService och sidan omdirigera till startsidan")
        void skaIdSkickasTillServiceOchOmdirigera() throws Exception {
            mockMvc.perform(post("/wines/1/radera").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));

            verify(wineService).removeWine(new WineId(1L), null);
        }

        /**
         * Måste vara ett FLASH-attribut, inte ett vanligt model-attribut -
         * samma resonemang som för lägg till/redigera (se
         * NärEttVinLäggsTill.skaSättaFlashMeddelande).
         */
        @Test
        @DisplayName("ska sätta ett flash-meddelande som visas efter omdirigeringen")
        void skaSättaFlashMeddelande() throws Exception {
            mockMvc.perform(post("/wines/1/radera").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("feedback", "Vin borttaget"));
        }
    }

    @Nested
    @DisplayName("när en bild visas")
    class NärEnBildVisas {

        @Test
        @DisplayName("ska bilden serveras med rätt Content-Type")
        void skaBildenSererasMedRättContentType() throws Exception {
            byte[] bilddata = new byte[]{1, 2, 3};
            when(wineService.findById(eq(new WineId(1L)), any()))
                    .thenReturn(Optional.of(BAROLO.withImage(bilddata, "image/jpeg")));

            mockMvc.perform(get("/wines/1/bild").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("image/jpeg"))
                    .andExpect(content().bytes(bilddata));
        }

        @Test
        @DisplayName("ska ge 404 om vinet saknar bild")
        void skaGe404OmVinetSaknarBild() throws Exception {
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.of(BAROLO));

            mockMvc.perform(get("/wines/1/bild").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("när redigeringsformuläret visas")
    class NärRedigeringsformuläretVisas {

        @Test
        @DisplayName("ska formuläret vara förifyllt med vinets uppgifter")
        void skaFormuläretVaraFörifylltMedVinetsUppgifter() throws Exception {
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.of(BAROLO));

            mockMvc.perform(get("/wines/1/redigera").with(user("admin").roles("ADMIN")).with(csrf()))
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

        /**
         * WINE-51: redan tillagda taggar ska renderas som förkryssade
         * chips (kryssrutan ÄR "ta bort"-kontrollen, se vin-formular.html)
         * - inte bara nämnda som text.
         */
        @Test
        @DisplayName("ska förkryssa vinets redan tillagda taggar som chips")
        void skaFörkryssaRedanTillagdaTaggar() throws Exception {
            Wine barolo = BAROLO.toBuilder().tags(Set.of("Favorit", "Festvin")).build();
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.of(barolo));

            mockMvc.perform(get("/wines/1/redigera").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("name=\"tags\" value=\"Favorit\" checked"),
                            containsString("name=\"tags\" value=\"Festvin\" checked")
                    )));
        }

        /**
         * WINE-44: raderingen (med sin bekräftelsedialog) ska bara vara
         * möjlig för ett redan sparat vin, inte på "lägg till"-formuläret
         * (se motsvarande test i NärFormuläretFörEttNyttVinVisas).
         */
        @Test
        @DisplayName("ska visa en \"Radera vinet\"-knapp med bekräftelsedialog mot rätt id")
        void skaVisaRaderaKnappMedBekräftelsedialog() throws Exception {
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.of(BAROLO));

            mockMvc.perform(get("/wines/1/redigera").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Radera vinet"),
                            containsString("<dialog"),
                            containsString("action=\"/wines/1/radera\"")
                    )));
        }
    }

    @Nested
    @DisplayName("när ett vin redigeras")
    class NärEttVinRedigeras {

        /**
         * WINE-50: "ownRating" postas som fri text och sparas rakt av,
         * utan tolkning mot Rating (till skillnad från
         * "munskankarnaRating" nedan, oförändrat).
         */
        @Test
        @DisplayName("ska alla fält skickas till WineService och sidan omdirigera till startsidan")
        void skaAllaFältSkickasTillServiceOchOmdirigera() throws Exception {
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.of(BAROLO));

            mockMvc.perform(post("/wines/1/redigera")
                            .with(user("admin").roles("ADMIN")).with(csrf())
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
                            .param("ownRating", "Mycket bra, men lite ungt just nu")
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
                    .ownRating("Mycket bra, men lite ungt just nu")
                    .systembolagetProductNumber("12345").systembolagetDescription("Beskrivning")
                    .munskankarnaReview("Recension").munskankarnaRating(Rating.R14_5)
                    .vivinoRating(new BigDecimal("4.1")).otherReference("https://example.com")
                    .location("Låda 2")
                    .build());
        }

        @Test
        @DisplayName("ska sätta ett flash-meddelande som visas efter omdirigeringen")
        void skaSättaFlashMeddelande() throws Exception {
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.of(BAROLO));

            mockMvc.perform(post("/wines/1/redigera")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("name", "Barolo")
                            .param("quantity", "3"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("feedback", "Ändringar sparade"));
        }

        @Test
        @DisplayName("ska lämna valfria fält som null när de inte fylls i")
        void skaLämnaValfriaFältSomNullNärDeInteFyllsI() throws Exception {
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.of(BAROLO));

            mockMvc.perform(post("/wines/1/redigera")
                            .with(user("admin").roles("ADMIN")).with(csrf())
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

        /**
         * WINE-51: en redigering ERSÄTTER hela tagglistan med det
         * formuläret faktiskt skickade in - en tidigare borttagen tagg
         * (dess kryssruta togs bort ur DOM:en innan formuläret skickades,
         * se vin-formular.html) finns alltså inte kvar bara för att den
         * fanns på vinet innan.
         */
        @Test
        @DisplayName("ska ersätta vinets taggar med de som skickades med formuläret")
        void skaErsättaTaggarna() throws Exception {
            Wine barolo = BAROLO.toBuilder().tags(Set.of("Gammal tagg")).build();
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.of(barolo));

            mockMvc.perform(post("/wines/1/redigera")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("name", "Barolo")
                            .param("quantity", "3")
                            .param("tags", "Favorit"))
                    .andExpect(status().is3xxRedirection());

            // Övriga fält blir null eftersom de inte skickades med -
            // samma "tomt formulärfält blir null"-regel som gäller
            // resten av applyFormFields (se skaLämnaValfriaFältSomNullNärDeInteFyllsI).
            verify(wineService).save(Wine.builder()
                    .id(new WineId(1L)).name("Barolo").quantity(3)
                    .tags(Set.of("Favorit"))
                    .build());
        }

        @Test
        @DisplayName("ska ersätta bilden om en ny fil väljs")
        void skaErsättaBildenOmEnNyFilVäljs() throws Exception {
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.of(BAROLO));
            byte[] nyBilddata = new byte[]{4, 5, 6};

            mockMvc.perform(multipart("/wines/1/redigera")
                            .file(new MockMultipartFile("bild", "ny-etikett.jpg", "image/jpeg", nyBilddata))
                            .with(user("admin").roles("ADMIN")).with(csrf())
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
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.of(vinMedBild));

            mockMvc.perform(post("/wines/1/redigera")
                            .with(user("admin").roles("ADMIN")).with(csrf())
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

    /**
     * WINE-14: den delen av dataisoleringen som hör till webblagret -
     * "nekas åtkomst som om vinet inte fanns" via direkt URL. `WineService`
     * är redan stubbad, så det som simuleras är "det här vinet finns inte
     * FÖR DEN INLOGGADE ANVÄNDAREN" (findById/findByIdAndOwner returnerar
     * tomt) - exakt vad WineService faktiskt returnerar när ett vin ägs av
     * någon annan (se WineService/WineRepository). Själva listans
     * osynlighet (den andra halvan av WINE-14) testas i
     * flera-anvandare.feature (Cucumber, applikationslagret) istället,
     * där ägarlogiken faktiskt körs på riktigt.
     */
    @Nested
    @DisplayName("dataisolering mellan användare")
    class DataiseleringMellanAnvändare {

        @Test
        @DisplayName("ska neka redigeringssidan för ett vin som tillhör en annan användare")
        void skaNekaRedigeringssidanFörAnnanAnvändaresVin() throws Exception {
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.empty());

            mockMvc.perform(get("/wines/1/redigera").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("ska inte spara en redigering för ett vin som tillhör en annan användare")
        void skaInteSparaRedigeringFörAnnanAnvändaresVin() throws Exception {
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.empty());

            mockMvc.perform(post("/wines/1/redigera")
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .param("name", "Barolo"))
                    .andExpect(status().isNotFound());

            verify(wineService, never()).save(any());
        }

        @Test
        @DisplayName("ska neka bildvisning för ett vin som tillhör en annan användare")
        void skaNekaBildvisningFörAnnanAnvändaresVin() throws Exception {
            when(wineService.findById(eq(new WineId(1L)), any())).thenReturn(Optional.empty());

            mockMvc.perform(get("/wines/1/bild").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }
}
