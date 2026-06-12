# DashBoardAdmin

Mod d'administration pour serveur SMP, sous NeoForge pour Minecraft 1.21.1.

## Téléchargement

**[Télécharger la v1.0.9 (.jar)](https://github.com/LKDM7/DashBoardAdmin/raw/master/releases/dashboardadmin-1.0.9.jar)**

Les versions précédentes sont disponibles dans le dossier [`releases/`](releases/). Tous les joueurs doivent utiliser la même version que le serveur (vérification réseau stricte).

## Fonctionnalités

- **Interface admin** : menu graphique (`/menu`) pour gérer les joueurs
- **Paramètres joueur** : écran de configuration des paramètres individuels
- **Inventaires virtuels** : gestion des coffres virtuels par joueur
- **Homes** : sauvegarde et téléportation aux points d'accueil
- **Zones** : création et gestion de zones protégées, visualisation en jeu (wireframe coloré + nom flottant), 10 règles par zone (construction, interaction, conteneurs, entrée, items, PvP, spawns, explosions, piétinement), priorités entre zones superposées, messages d'entrée/sortie configurables
- **Groupes** : équipes de joueurs
- **Sanctions** : gestion des avertissements et sanctions
- **Deals** : échanges entre joueurs
- **HUD** : affichage en temps réel (groupes, notifications)

## Installation

1. Installe [NeoForge](https://neoforged.net/) `21.1.232+` pour Minecraft 1.21.1
2. Place le fichier `.jar` dans le dossier `mods/` de ton serveur/client

## Compilation

```bash
./gradlew build
```

Le fichier `.jar` généré se trouve dans `build/libs/`.

## Infos

- **Version du mod :** 1.0.9
- **Version Minecraft :** 1.21.1
- **Loader :** NeoForge `21.1.232`
- **Auteur :** LKDM
- **Licence :** All Rights Reserved
