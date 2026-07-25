package com.example.winecellar.acceptance;

import com.example.winecellar.application.RegistrationResult;
import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.infrastructure.JpaUserRepository;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Körs mot Spring-hanterade bönor och en riktig Postgres via
 * Testcontainers, samma mönster som PersistenceSteps - WINE-11 (se
 * ADR 0013). Bara RegistrationServices kärnlogik (kontoskapande,
 * unikhetskontroll) testas här; sessionen/auto-inloggningen efter
 * registrering hör till webblagret och testas i
 * RegistrationControllerTest istället.
 */
public class RegistrationSteps {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JpaUserRepository userRepository;

    private RegistrationResult senasteResultat;

    /**
     * `order = 0` - mittersta steget av en tredelad, klassöverskridande
     * städordning; se {@code PersistenceSteps.raderaAllaViner()}s Javadoc
     * för den fullständiga motiveringen (varför viner måste raderas
     * FÖRE users, och users FÖRE ett nytt testkonto registreras).
     */
    @Before(order = 0)
    public void reset() {
        userRepository.deleteAll();
    }

    @Givet("att inget konto med användarnamnet {string} finns")
    public void attIngetKontoMedAnvändarnamnetFinns(String username) {
        assertThat(userRepository.findByUsername(username)).isEmpty();
    }

    @Givet("att ett konto med användarnamnet {string} redan finns")
    public void attEttKontoMedAnvändarnamnetRedanFinns(String username) {
        registrationService.register(username, "ettLösenord123");
    }

    @När("jag registrerar mig med användarnamnet {string} och lösenordet {string}")
    public void jagRegistrerarMigMedAnvändarnamnetOchLösenordet(String username, String password) {
        senasteResultat = registrationService.register(username, password);
    }

    @När("jag försöker registrera mig med användarnamnet {string}")
    public void jagFörsökerRegistreraMigMedAnvändarnamnet(String username) {
        senasteResultat = registrationService.register(username, "ettAnnatLösenord123");
    }

    @Så("skapas ett konto med användarnamnet {string}")
    public void skapasEttKontoMedAnvändarnamnet(String username) {
        assertThat(senasteResultat).isInstanceOf(RegistrationResult.Registered.class);
        assertThat(userRepository.findByUsername(username)).isPresent();
    }

    @Så("nekas registreringen på grund av att användarnamnet är upptaget")
    public void nekasRegistreringenPåGrundAvAttAnvändarnamnetÄrUpptaget() {
        assertThat(senasteResultat).isInstanceOf(RegistrationResult.UsernameTaken.class);
    }
}
