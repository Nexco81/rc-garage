# Étape 3 — Collection, stocks et affectations (Codex)

## Point de départ
Branche `feat/android-collection-issue-3` issue de `android-development` après la PR #12. Ne pas toucher à `main`, `index.html` ni à l'application web. Implémenter dans `android/` exclusivement. PR vers `android-development`, sans fusion automatique.

## Objectif
Rendre les 25 modèles importés consultables et modifiables hors ligne, ainsi que les équipements, radios, servos de direction et batteries. Réutiliser Room et les identifiants importés, sans réinitialiser les données existantes. Ne pas inventer de données absentes.

## Modèles RC
- Liste avec recherche/filtrage et fiche détaillée éditable : marque, nom/référence, châssis, statut, usage, notes, carrosseries, équipement.
- Champs marque : saisie libre + suggestions de marques déjà enregistrées ; châssis : saisie libre + suggestions filtrées par la marque sélectionnée (jamais TT-02 pour Traxxas par défaut).
- Statut RTR conservé indépendamment de l'électronique. RTR d'origine : afficher « Origine » et masquer les détails électroniques ; aucune création d'équipements fictifs. Quand l'utilisateur active la personnalisation RTR, permettre des équipements propres ; masquer/réafficher les affectations sans effacer les valeurs si le statut est changé.
- Préserver les modèles vendus et leurs informations historiques.

## Équipements et stocks
- Fiches équipements par catégorie : radios, récepteurs, ESC, moteurs, servos de direction ; batteries selon les entités disponibles.
- Marque avant nom/référence, saisie libre et suggestions issues des marques existantes, aussi pour carrosseries.
- Quantité possédée, affectée, disponible ; quantités inconnues restent inconnues. Empêcher les allocations physiques excédentaires sans bloquer les radios multi-modèles.
- Radios : une radio physique peut être associée à plusieurs modèles, sans consommation de stock par modèle ; mémoire radio facultative.
- Dans chaque fiche équipement, afficher les modèles qui l'utilisent. Réaffectation, retrait et historique cohérents, avec transactions Room lorsque nécessaire.
- Servo de direction : gestion identique aux ESC, avec plusieurs références en stock.

## UI/qualité
- Jetpack Compose, navigation fonctionnelle depuis l'accueil, états vides/chargement/erreur, édition et sauvegarde explicites.
- Conserver l'import JSON fonctionnel et l'icône officielle. Pas de Firebase requis à cette étape.
- Tests JVM pour lecture/édition/persistance, filtres de châssis, RTR, radio partagée, servos, stock insuffisant et non-régression de l'import ; compilation/lint/CI et APK debug.
- Ne pas inventer une migration destructrice. Documenter les choix de schéma et les limitations.

## Livrable
Code réel, tests et documentation ; PR séparée vers `android-development` sans fusion automatique. Si la portée est trop importante, livrer un sous-ensemble cohérent et signaler explicitement ce qui reste à faire.
