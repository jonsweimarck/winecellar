package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.infrastructure.InMemoryUserRepository;
import com.example.winecellar.infrastructure.InMemoryWineRepository;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WINE-14: dataisolering mellan användare. Egen stegklass (inte
 * återanvänd WineService-instans från övriga stegklasser, se
 * CLAUDE.md om varför delade Gherkin-steg måste ligga i en och samma
 * klass) - de här scenarierna är de enda som modellerar FLERA
 * inloggade användare samtidigt, ett begrepp ingen annan stegklass
 * behöver.
 */
public class MultiUserSteps {

    private WineService wineService;
    private InMemoryUserRepository userRepository;
    private final Map<String, UserId> userIdsByUsername = new HashMap<>();

    private List<Wine> shownList;

    @Before
    public void setUp() {
        wineService = new WineService(new InMemoryWineRepository());
        userRepository = new InMemoryUserRepository();
    }

    @Givet("att användaren {string} har lagt till vinet {string}")
    public void attAnvändarenHarLagtTillVinet(String username, String wineName) {
        wineService.save(StepSupport.wineWithName(wineName).toBuilder()
                .owner(userIdFor(username))
                .build());
    }

    /**
     * Bara till för läsbarhet i Gherkin-scenariot ("och att användaren
     * X är inloggad") - kontot skapas redan lat av userIdFor(...) första
     * gången namnet nämns, oavsett om det är i det här steget eller ett
     * "har lagt till"-steg. Inget separat inloggat/aktivt tillstånd.
     */
    @Givet("att användaren {string} är inloggad")
    public void attAnvändarenÄrInloggad(String username) {
        userIdFor(username);
    }

    private UserId userIdFor(String username) {
        return userIdsByUsername.computeIfAbsent(username,
                name -> userRepository.save(new User(null, name, "irrelevant-i-testet", Instant.now())).id());
    }

    @När("{string} öppnar sin vinlista")
    public void öppnarSinVinlista(String username) {
        shownList = wineService.listWines(userIdFor(username));
    }

    @Så("syns inte {string} i listan")
    public void synsInteIListan(String wineName) {
        assertThat(shownList).extracting(Wine::name).doesNotContain(wineName);
    }
}
