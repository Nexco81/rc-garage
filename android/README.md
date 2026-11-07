# RC Garage Android

Nouvelle application Android indépendante de l'application web historique.

**Sécurité de migration** : travailler uniquement sur la branche `android-development` dans le répertoire `android/`. Ne pas modifier `index.html` ni le workflow GitHub Pages de `main`.

## Architecture cible
- Kotlin, Jetpack Compose, Material 3, navigation Compose
- Room + SQLite : source locale de vérité, fonctionnement offline-first
- Repositories + ViewModels + StateFlow, séparation UI / domaine / données
- Firebase Authentication (Google), Cloud Firestore (sync), Cloud Storage (photos)
- WorkManager pour synchronisation différée, Firebase Security Rules pour isolation des comptes
- Tests unitaires des mouvements de stock, tests Room et tests Compose

## Modules prévus
`app` (navigation/UI), `core:model`, `core:database`, `core:domain`, `core:sync`, `feature:collection`, `feature:inventory`, `feature:bodies`, `feature:batteries`, `feature:archive`, `feature:backup`.

## Ordre d'exécution
1. Initialiser le projet Gradle et un APK debug démarrant hors ligne.
2. Définir le schéma Room et les migrations.
3. Importer la sauvegarde JSON de l'ancienne application dans un espace de test.
4. Implémenter collection, fiches et équipements, avec quantités et affectations.
5. Implémenter les ventes et annulations transactionnelles, puis les tests.
6. Ajouter photos, batteries, carrosseries, JSON/CSV.
7. Ajouter l'authentification et la synchronisation Firebase, puis la distribution APK.

Le cahier des charges PDF validé le 07/11/2026 est la référence fonctionnelle. Ne jamais committer des sauvegardes personnelles, clés Firebase, keystores ou fichiers de configuration secrets.

## Projet initial (tâche #1)

Ouvrir `android/` dans Android Studio. Prérequis : JDK 17 (ou JDK 21),
SDK Android 35 et Build Tools 35.0.0. Le wrapper utilise Gradle 8.11.1
avec vérification SHA-256, AGP 8.9.2 et Kotlin/Compose Compiler 2.1.20.
Configurer `ANDROID_HOME` ou le fichier ignoré `local.properties` avec `sdk.dir`.

Depuis `android/` :

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
# Nécessite un appareil ou émulateur Android API 31 minimum :
./gradlew :app:connectedDebugAndroidTest
```

L'APK se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.
L'accueil est autonome et fonctionne sans connexion ni configuration Firebase.
Le bouton « Apparence » ouvre les choix système, clair et sombre ; le choix est
conservé localement entre les lancements. Aucun inventaire de démonstration
ni donnée personnelle n'est embarqué. Room fournit désormais le modèle local et l’import historique (étape 2) ;
les écrans de gestion et les commandes métier arrivent dans les lots suivants.

La CI `.github/workflows/android.yml` compile l'APK et les tests instrumentés,
exécute les tests JVM et lint, sans secret ni publication. Les tests instrumentés
sont compilés en CI mais doivent être exécutés sur appareil/émulateur séparément.

## Modèle local et import historique (étape 2)

L'écran **Importer une sauvegarde JSON** permet maintenant de prévisualiser et
confirmer un import atomique dans Room. Les données ne sont pas préchargées ;
le jeu de test 25/6/1 reste dans les ressources de tests uniquement.
Voir [décisions, schémas, règles d'import et vérifications](docs/STEP_02_IMPLEMENTATION.md).
Le processeur Room utilise KSP ; les schémas versionnés sont sous `app/schemas/`.

Correction de l’import historique : [quantités textuelles, radios partagées et migration v3](docs/LEGACY_IMPORT_CORRECTION.md).

## Collection et stocks (étape 3)

Depuis l'accueil : **Collection et stocks**. Les données Room importées existantes
sont conservées, avec formulaires de sauvegarde explicite, inventaires et
transactions d'affectation. Voir [parcours, protections, tests et limites](docs/STEP_03_IMPLEMENTATION.md).
Les ventes/restaurations restent hors périmètre ; les archives sont en lecture seule.
