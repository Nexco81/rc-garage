# Codex — Étape 2 : Room, import historique et tests

Issue : https://github.com/Nexco81/rc-garage/issues/2
Branche de travail : `feat/android-room-issue-2` (issue de `android-development`). PR cible : `android-development` ; ne jamais fusionner automatiquement.

## Périmètre strict
- Modifier exclusivement des fichiers sous `android/`. Ne toucher ni au site web, ni à `main`, ni aux workflows hors `android/`.
- Lire d'abord `android/docs/ARCHITECTURE.md` et `android/docs/CODEX_TASKS.md`. Respecter le cahier des charges ; documenter toute ambiguïté plutôt que d'inventer des données.
- Kotlin + Room : entités, clés étrangères, index, DAO suspend/Flow, transactions, repositories, convertisseurs et migrations explicites testables. Ne pas autoriser `fallbackToDestructiveMigration`.
- Modéliser véhicules (Kit/RTR comme propriété durable, électronique d'origine masquable sans effacement), références d'équipements et unités/quantités, affectations multiples et historique, servos, ESC, moteurs, récepteurs, radios, batteries et carrosseries selon l'architecture. Une fiche équipement doit pouvoir lister les véhicules affectés. Les équipements RTR d'origine ne doivent pas être créés artificiellement.
- Garantir intégrité des stocks et liens ; prévoir la vente/restauration transactionnelle du lot 4 sans l'implémenter entièrement.
- Import JSON historique : lecture sans modification de l'original, prévisualisation, validation explicite, déduplication déterministe, transaction atomique et rapport d'erreurs ; ne pas deviner les champs manquants. Préserver les historiques inconnus. Jeu anonymisé : 25 modèles, 6 références électroniques, 1 carrosserie. Règle spécifique Senton : ne compléter le moteur d'origine que si la source/architecture le justifie.
- Tests JVM Room/DAO/repository et migration (instrumentés si nécessaire), tests import et rollback, vérifications Gradle assembleDebug/test/lint ; ne pas affirmer que les tests instrumentés ont été exécutés sans émulateur.
- Mettre à jour documentation Android uniquement. Aucune configuration Firebase ou secret dans ce lot.

## Livrable
Code compilable, tests, résumé des décisions et limites, PR ciblée vers `android-development` avec résultats CI, sans fusion automatique. Si l'environnement Codex ne peut pas lancer la tâche directement, utiliser ce fichier comme consigne d'implémentation.
