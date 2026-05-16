# DashBoardAdmin

Mod d'administration complet pour serveur SMP, développé avec Fabric pour Minecraft 1.21.10.

## Fonctionnalités

- **Interface admin** — menu graphique (`/menu`) pour gérer les joueurs
- **Paramètres joueur** — écran de configuration des paramètres individuels
- **Inventaires virtuels** — gestion des coffres virtuels par joueur
- **Système de homes** — sauvegarde et téléportation aux points d'accueil
- **Zones** — création et gestion de zones protégées
- **Groupes** — système de groupes/équipes de joueurs
- **Sanctions** — gestion des avertissements et sanctions
- **Deals** — système d'échanges entre joueurs
- **HUD** — affichage d'informations en temps réel (groupes, notifications)

## Installation

1. Installe [Fabric Loader](https://fabricmc.net/use/) `0.19.2+`
2. Installe [Fabric API](https://modrinth.com/mod/fabric-api) `0.138.4+1.21.10`
3. Place le fichier `.jar` dans le dossier `mods/` de ton serveur/client

## Compilation

```bash
./gradlew build
```

Le fichier `.jar` généré se trouve dans `build/libs/`.

## Infos

- **Version Minecraft :** 1.21.10
- **Loader :** Fabric `0.19.2`
- **Auteur :** LKDM
- **Licence :** All Rights Reserved
