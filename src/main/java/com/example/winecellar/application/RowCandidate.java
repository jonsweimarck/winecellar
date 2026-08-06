package com.example.winecellar.application;

import com.example.winecellar.domain.Wine;

/**
 * En rad från importfilen som har kunnat tolkas till ett {@link Wine},
 * tillsammans med det ursprungliga radnumret i Excel-filen. Radnumret
 * behövs för att rapportera interna dubbletter inom samma fil, men
 * används inte efter den kontrollen.
 */
public record RowCandidate(int rowNumber, Wine wine) {
}
