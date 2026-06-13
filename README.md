# DashBoardAdmin

Mod d'administration pour serveur SMP, sous NeoForge pour Minecraft 1.21.1.

## Téléchargement

**[Télécharger la v1.3.0 (.jar)](https://github.com/LKDM7/DashBoardAdmin/raw/master/releases/dashboardadmin-1.3.0.jar)**

Les versions précédentes sont disponibles dans le dossier [`releases/`](releases/). Tous les joueurs doivent utiliser la même version que le serveur (vérification réseau stricte).

## Fonctionnalités

- **Interface admin** : menu graphique (`/admin`) — joueurs (en ligne et hors ligne, fiche d'activité avec notes de modération multiples), santé serveur (TPS, RAM, entités), historique du chat persistant, spy des messages privés, restart programmé, warps, MOTD
- **Rôles de modération** : création de rôles personnalisés avec permissions (onglets + actions sensibles) et assignation de joueurs ; les modérateurs ouvrent `/admin` sans OP et n'accèdent qu'à ce que leur rôle autorise
- **Paramètres joueur** : écran de configuration des paramètres individuels
- **Inventaires virtuels** : gestion des coffres virtuels par joueur
- **Homes & warps** : points personnels et warps publics, téléportation depuis le menu
- **Zones** : création et gestion de zones protégées, visualisation en jeu (wireframe coloré + nom flottant), 10 règles par zone (construction, interaction, conteneurs, entrée, items, PvP, spawns, explosions, piétinement), priorités entre zones superposées, messages d'entrée/sortie configurables
- **Groupes** : équipes de joueurs
- **Sanctions** : gestion des avertissements et sanctions
- **Deals** : échanges entre joueurs
- **HUD** : affichage en temps réel (groupes, notifications)
- **Bilingue** : interfaces en français ou en anglais selon la langue du jeu

## Installation

1. Installe [NeoForge](https://neoforged.net/) `21.1.232+` pour Minecraft 1.21.1
2. Place le fichier `.jar` dans le dossier `mods/` de ton serveur/client

## Compilation

```bash
./gradlew build
```

Le fichier `.jar` généré se trouve dans `build/libs/`.

## Infos

- **Version du mod :** 1.3.0
- **Version Minecraft :** 1.21.1
- **Loader :** NeoForge `21.1.232`
- **Auteur :** LKDM
- **Licence :** All Rights Reserved
