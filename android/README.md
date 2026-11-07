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
