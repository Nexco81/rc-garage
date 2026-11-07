# Ordre de travail Codex

## Lot 1 — Projet compilable
Créer un projet Android Gradle sous `android/`, Kotlin, Compose Material 3, navigation, thèmes clair/sombre/système, minSdk 31. Un écran d'accueil hors ligne suffit au départ. Ajouter un workflow CI déclenché sur `android/**`, sans secrets ni publication.

**Critère** : `./gradlew :app:assembleDebug` passe et `main/index.html` reste inchangé.

## Lot 2 — Modèle local et migration
Créer les entités Room, DAO, repositories et migrations testables. Implémenter l'import de l'export JSON historique (avec aperçu, déduplication et validation). Conserver une copie originale de l'import.

**Critère** : importer 25 modèles, 6 références électroniques et 1 carrosserie depuis le jeu de test ; normaliser les statuts et compléter le moteur d'origine du Senton sans inventer les autres données.

## Lot 3 — Collection et inventaires
Fiches, filtres, listes de suggestions de marque/châssis, Kit/RTR, composants multiples, radios et batteries. Compteurs +/− et « Quantité possédée ». Afficher les véhicules affectés dans chaque fiche équipement.

## Lot 4 — Ventes et restauration
Vente complète ou partielle, historique figé, conservation d'une photo, mise à jour atomique des stocks, restauration avec choix par composant. Tester vente/conservation/réaffectation/annulation et rollback sur conflit.

## Lot 5 — Carrosseries, photos, export
Carrosseries d'origine et indépendantes, compression photo (5 par véhicule), JSON complet, CSV à choix de rubrique (UTF-8, point-virgule), corbeille 30 jours.

## Lot 6 — Firebase et APK
Connexion Google, Firestore/Storage isolés par compte, synchronisation hors ligne, conflits explicites, suppression de compte, distribution APK signé et mise à jour sûre.

**Consigne générale** : créer des PR ciblées depuis `android-development`. Ne jamais pousser sur `main` et ne jamais modifier le site web historique.
