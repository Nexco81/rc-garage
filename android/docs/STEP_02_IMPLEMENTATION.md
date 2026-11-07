# Étape 2 — modèle local et import historique

## Utilisation

Depuis l'accueil, ouvrir **Importer une sauvegarde JSON**, choisir le fichier avec
le sélecteur Android, vérifier les compteurs, avertissements et erreurs, puis
**Confirmer l'import**. L'aperçu ne modifie pas la base. Les erreurs bloquent la
confirmation. Annuler ne crée aucune donnée. La taille maximale est 5 Mio et la
profondeur JSON 64 ; les lectures sont bornées et faites hors du thread UI.
L'application ne demande aucun accès Internet, compte ni secret.

Après confirmation, les compteurs du dépôt Room sont réactifs. Le parcours
collection/inventaire éditable appartient au lot 3. La préférence de thème et
la navigation existantes sont conservées. Le ViewModel conserve l'aperçu pendant
une rotation ; après destruction du processus, il faut choisir de nouveau le fichier.

## Modèle et intégrité

`data/Entities.kt` contient véhicules, références, unités identifiées, affectations,
mémoires radio, carrosseries, batteries/compatibilités, photos, ventes/lignes,
mouvements et changements de synchronisation. UUID typés, clés étrangères et index
sont exportés dans `app/schemas/`. Les unités individuelles sont facultatives :
un ancien identifiant de lot ne crée pas de numéros de série imaginaires.

Les DAO proposent des écritures suspendues et des lectures Flow, notamment les
véhicules affectés à une référence et son historique. Le repository fournit
l'import atomique et les opérations locales d'affectation/libération/quantité.
Les triggers SQLite protègent aussi les appels directs aux DAO : quantité non
négative, rôle compatible, unités appartenant à la référence et non allouées deux
fois, stocks suffisants, relations radio/récepteur, mémoires uniques par radio et
emplacement, provenance des carrosseries d'origine et photos principales cohérentes.

Les radios sont partagées : une référence radio peut servir plusieurs véhicules,
avec des mémoires distinctes, sans consommer une radio par véhicule. Les autres
composants consomment la somme des quantités des affectations actives. Plusieurs
composants d'un même rôle peuvent être affectés au même véhicule. Libérer désactive
la ligne ; modifier/réactiver/supprimer l'historique est interdit. Les journaux de
vente, lignes, mouvements et fichiers importés sont immuables.

`isRtr` et `electronicsCustomized` sont des propriétés durables et nullable si la
source est muette. L'électronique d'origine explicite reste dans
`originalElectronicsJson` même lorsqu'elle est masquée par une configuration
personnalisée. Aucun équipement de stock fictif n'est créé pour un RTR.
Une carrosserie explicitement déclarée de boîte est attachée au véhicule avec
provenance ; les carrosseries indépendantes restent dans l'inventaire. Sans preuve,
leur origine reste inconnue (`isOriginal = null`).

Les tables vente/restauration et stock sont préparées, mais les commandes métier
`SellVehicle` et `RestoreSale`, les résolutions de conflits et la synchronisation
Firebase restent les lots 4 et 6. Une transaction future pourra grouper libérations,
affectations et journal. Aucun algorithme « dernier arrivé gagne » n'est ajouté.

## Format historique et décisions

- Objet JSON UTF-8 (BOM accepté), `models` et `gear` requis, `bodyStock` facultatif.
  Les champs de l'export web sont lus ; les rubriques supplémentaires sont signalées
  et restent disponibles dans l'original. Ce n'est pas encore l'import d'un export
  natif Android ou un import autonome de batteries.
- Identifiant source obligatoire. Un UUID valide est conservé. Les anciens IDs
  sont convertis par UUID déterministe avec espace de noms par type. Pas de fusion
  approximative par marque/nom : deux IDs distincts restent distincts.
- Les doublons identiques sont ignorés ; les doublons contradictoires, liens cassés,
  types incorrects, doubles affectations et stocks insuffisants produisent des erreurs.
  Un conflit avec une donnée déjà en base annule **toute** la transaction.
- Confirmation : nouvelle analyse des mêmes octets, puis contrôle des conflits dans
  une transaction Room. Un même SHA-256 est un no-op. Un JSON reformatté retrouve les
  mêmes UUID ; ses lignes identiques ne sont pas réinsérées. Il peut avoir sa propre
  copie originale puisqu'il s'agit d'un fichier différent.
- Les octets exacts, SHA-256, nom du document, date d'import et rapport sont stockés
  atomiquement dans `import_sources`. Le fichier choisi n'est jamais modifié. Un import
  invalide/annulé n'est pas archivé dans la base. L'original contient aussi les champs
  historiques non interprétés (par exemple une mémoire radio non numérique).
- Quantité absente : inconnue (`null`), jamais implicitement 1. Une quantité inconnue
  ne permet pas d'affectation active pour les composants consommant du stock ; les
  radios partagées sont indépendantes de la quantité physique. Les dates absentes restent nulles. Statut non
  reconnu : `UNKNOWN` et libellé source conservé. Marque et nom du véhicule requis.
- Véhicule vendu historique : vente `UNKNOWN`, `historyKnown=false`, date inconnue,
  snapshot immuable, aucune conclusion « vendu complet ». Ses anciennes affectations
  sont conservées inactives, sans consommer le stock actuel.
- **Senton** : aucune référence de moteur n'est justifiée par les documents fournis
  ou le seul nom « Senton 223S V4 ». Aucune référence constructeur n'est inventée.
  Si `originalElectronics.motor` existe dans le JSON, elle est conservée et testée,
  y compris lorsque l'électronique personnelle est activée.
- Le jeu `src/test/resources/legacy-anonymized.json` est synthétique, sans sauvegarde
  personnelle : 25 modèles, 6 références, 1 carrosserie indépendante. Un autre
  fichier contenant des carrosseries de boîte/inline explicites peut naturellement
  produire davantage de carrosseries ; le nombre 1 n'est pas codé dans l'importeur.

## Schémas et migration

Le lot 2 a introduit la version **2**. Le correctif utilise désormais la version
**3**, décrite dans [la correction de l’import](LEGACY_IMPORT_CORRECTION.md). La v1 est la référence du modèle local sans les
copies originales ; aucune v1 Room n'avait été distribuée par l'étape 1. Son schéma
est un export réel du processeur Room, figé sous `GarageDatabase/1.json` avant ajout
de `import_sources`. Le test ouvre une base SQLite à partir de cet export figé,
insère des données et affectations, puis l'ouvre avec Room courant et les migrations explicites 1→2→3.
Room vérifie la totalité du schéma attendu. Le test vérifie les données conservées,
le stock disponible, les nouveaux triggers et le refus d'ouvrir sans migration.
La v2 ajoute les reçus immuables et installe les protections SQL. Les créations
neuves installent les mêmes protections via callback. Aucune migration destructive.
Ne pas régénérer les anciens schémas depuis les entités courantes : pour les futures
évolutions, augmenter la version et ajouter une migration testée.

## Vérifications

Depuis `android/`, avec un JDK complet 17/21 et SDK 35 :

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
# Appareil/émulateur API 31+ requis pour la suite Compose :
./gradlew :app:connectedDebugAndroidTest
```

La suite JVM utilise Room et SQLite sous Robolectric API 32. Elle couvre aperçu
sans écriture, 25/6/1, conservation exacte, déduplication, erreurs, Kit/RTR/Senton,
historiques inconnus, liens radios, quantités inconnues, composants multiples,
stock, unités, clés étrangères, immutabilité, rollback et migration.
Les tests Compose sont compilés ; ne pas confondre compilation et exécution.

Sur la machine cloud à répertoires système en lecture seule, les caches Gradle,
Android et le home JVM de Robolectric doivent être redirigés vers un répertoire
accessible ; conserver les réglages proxy/CA et TLS. Ce sont des réglages locaux,
pas des contournements de tests ni des paramètres obligatoires du projet.

## Résultat local de cette implémentation

- `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` réussis.
- **15 tests JVM exécutés, 0 échec, 0 ignoré** : 12 import/intégrité, 1 migration,
  2 thèmes existants. Tests Room exécutés sous Robolectric avec SQLite.
- Pas d'appareil/émulateur disponible : tests Compose instrumentés compilés mais
  **non exécutés**. Le sélecteur de fichiers et le parcours tactile complet restent
  à vérifier sur appareil ; les règles d'import et transactions ont été exécutées.
- Lint ne signale aucune erreur ; les avertissements non bloquants déjà présents
  concernent les règles de sauvegarde Android 12+, l'icône et la suggestion KTX.
