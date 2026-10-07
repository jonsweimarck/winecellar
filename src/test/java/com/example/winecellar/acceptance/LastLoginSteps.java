package com.example.winecellar.acceptance;

import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Och;
import io.cucumber.java.sv.Så;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Senaste login (WINE-62). Loggar in genom den RIKTIGA säkerhetskedjan
 * (POST /login via MockMvc mot hela Spring-kontexten) och riktig Postgres,
 * så att både händelselyssnaren och schema.sql:s NOT NULL bevisas på riktigt.
 * Registrering och admin-hjälpsteg återanvänds från RegistrationSteps/AdminSteps.
 */
public class LastLoginSteps {

    private static final String PASSWORD = "ettLösenord123";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @Before
    public void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    @Och("att senaste login för {string} är satt till {word} {word} UTC")
    public void attSenasteLoginÄrSattTill(String username, String date, String time) {
        jdbcTemplate.update("update users set last_login_at = ? where username = ?",
                java.sql.Timestamp.from(instant(date, time)), username);
    }

    @När("{string} loggar in med rätt lösenord")
    public void loggarInMedRättLösenord(String username) throws Exception {
        mockMvc.perform(post("/login").param("username", username).param("password", PASSWORD).with(csrf()));
    }

    @När("{string} försöker logga in med fel lösenord")
    public void försökerLoggaInMedFelLösenord(String username) throws Exception {
        mockMvc.perform(post("/login").param("username", username).param("password", "fel").with(csrf()));
    }

    @Så("ska senaste login för {string} vara samma tidpunkt som när kontot skapades")
    public void skaVaraSammaSomSkapad(String username) {
        User user = user(username);
        assertThat(user.lastLoginAt()).isNotNull().isEqualTo(user.createdAt());
    }

    @Så("ska senaste login för {string} vara senare än {word} {word} UTC")
    public void skaVaraSenareÄn(String username, String date, String time) {
        Instant lastLogin = user(username).lastLoginAt();
        assertThat(lastLogin).isAfter(instant(date, time));
        assertThat(lastLogin).isAfter(Instant.now().minusSeconds(60));
    }

    @Så("ska senaste login för {string} fortfarande vara {word} {word} UTC")
    public void skaFortfarandeVara(String username, String date, String time) {
        assertThat(user(username).lastLoginAt()).isEqualTo(instant(date, time));
    }

    @Så("ska databasen vägra att tömma senaste login för {string}")
    public void skaVägraTömma(String username) {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update users set last_login_at = null where username = ?", username))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    private static Instant instant(String date, String time) {
        return LocalDateTime.parse(date + " " + time, FORMAT).toInstant(ZoneOffset.UTC);
    }
}
