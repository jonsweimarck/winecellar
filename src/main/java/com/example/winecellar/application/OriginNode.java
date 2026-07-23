package com.example.winecellar.application;

import java.util.List;

/**
 * En nod i land->region->underregion-trädet som filterpanelen renderar
 * som nästlade kryssrutor. Samma rekursiva form på alla tre nivåer
 * (underregion är bara en nod utan barn) - härleds fräscht från
 * WineService.originTree() vid varje anrop, lagras inte i databasen
 * (se CLAUDE.md om varför land/region/underregion är fri text, inte en
 * uppslagstabell).
 */
public record OriginNode(String name, List<OriginNode> children) {
}
