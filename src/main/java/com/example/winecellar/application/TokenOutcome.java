package com.example.winecellar.application;

/** Utfall när en användare försöker lösa in ett verifierings-/återställningstoken. */
public enum TokenOutcome {
    SUCCESS,
    EXPIRED,
    INVALID
}
