package com.example.winecellar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WinecellarApplication {

    // Deploy-diagnos 2026-09-26: tom kommentar, pushad direkt till master för
    // att verifiera att Clever Clouds autodeploy faktiskt triggas och lyckas.
    // Tas bort igen efter att deployen är bekräftad.

    public static void main(String[] args) {
        SpringApplication.run(WinecellarApplication.class, args);
    }
}
