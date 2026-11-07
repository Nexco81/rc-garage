# Étape 3 — Collection et stocks hors ligne

## Parcours disponibles

L'accueil ouvre **Collection et stocks**, avec quatre rubriques : Collection,
Stocks, Carrosseries, Batteries. L'import JSON et l'apparence restent accessibles
sur l'accueil ; l'icône officielle est conservée.

- **Collection** : recherche (marque/nom/châssis/usage/notes), filtre de statut,
  liste complète incluant les vendus, détails, création et édition explicite.
  Marque libre avec suggestions enregistrées ; suggestions de châssis uniquement
  pour la marque sélectionnée. Aucune liste constructeur ni valeur par défaut
  n'est inventée. Les champs non exposés (catégorie/échelle, photo principale,
  électronique d'origine, référence de vente) sont conservés lors d'une édition.
- **Kit/RTR** : valeurs inconnues conservées ; RTR et personnalisation sont deux
  propriétés distinctes. RTR sans personnalisation active affiche « Origine » et
  masque les détails personnels. Les affectations restent enregistrées ; les composants qui consomment du stock
  restent réservés. Le simple changement de visibilité ne libère ni n'efface rien.
  Modifier une affectation nécessite de sauvegarder d'abord la personnalisation.
- **Stocks** : catégories radios, récepteurs, ESC, moteurs et servos de direction,
  recherche, création/édition, marque avant référence et suggestions libres.
  Quantités possédées/affectées/disponibles ; une quantité vide reste inconnue.
  Les boutons +/− modifient le brouillon d'une quantité connue, sans écriture
  avant Enregistrer. Plusieurs références et plusieurs composants d'un même rôle
  par véhicule sont supportés.
- **Radios** : les associations n'utilisent pas le stock physique, même si la
  quantité possédée est inconnue ou zéro. Mémoire facultative, unique par radio et
  emplacement. Une même radio peut servir plusieurs modèles ; l'écran distingue
  le nombre de modèles associés de la quantité physique.
- **Affectations** : ajout, modification de quantité/mémoire, réaffectation et
  retrait explicitement confirmés. Les anciennes lignes sont désactivées, jamais
  effacées/réactivées. Les stocks et mémoires sont mis à jour dans une transaction
  unique ; un conflit annule aussi les libérations précédentes. Les récepteurs
  liés doivent être retirés avant leur radio. Retirer un récepteur conserve le
  numéro de mémoire et efface seulement son lien devenu invalide ; retirer la
  dernière association radio retire la mémoire courante correspondante.
- **Fiche équipement** : tous les modèles utilisateurs et les lignes d'affectation,
  ainsi que l'historique actif/retiré (dates absentes affichées inconnues).
- **Carrosseries** : fiches, marque libre/suggestions, nom, référence, état,
  décoration, notes et modèle actuel. Le retrait vers le stock conserve la
  provenance d'origine ; aucune origine n'est inventée pour une nouvelle
  carrosserie indépendante. Les photos/compatibilités textuelles existantes
  restent consultables et conservées, sans traitement de photos à cette étape.
- **Batteries** : fiches et création/édition des champs disponibles dans Room
  (marque, référence, chimie, capacité, cellules, tension, C, connecteur, quantité),
  avec compatibilités aux modèles. Ces compatibilités ne sont pas des réservations
  physiques : le schéma actuel n'a pas d'affectation de batterie, et l'écran ne
  prétend pas calculer un stock disponible physique à partir des compatibilités.

## Conservation et transactions

Aucun seed, reset, suppression de base ou import automatique. Les 25 modèles déjà
importés sont lus dans le même fichier `rc-garage.db`, avec leurs UUID. La création
d'une fiche n'est effectuée que par une sauvegarde explicite. Annuler un formulaire
n'écrit rien. Les brouillons restent dans l'éditeur pendant une rotation ; ils
ne sont pas promis après destruction du processus.

Le schéma reste **v3** ; aucune migration n'est nécessaire, les entités et schémas
v1/v2/v3 ne changent pas. Un DAO d'édition est ajouté ; les règles SQLite, clés
étrangères et journaux immuables restent actifs. Les écritures passent par
`CollectionRepository` dans une transaction Room. Les fiches sont vérifiées contre
leur version d'ouverture pour refuser l'écrasement par un éditeur périmé. Les
snapshots Flow de l'ensemble de la collection sont lus dans une transaction,
y compris après une édition qui ne change aucun compteur de lignes.

Les originaux JSON, spécifications importées et journaux de vente ne sont pas
réécrits. Un réimport du même fichier reste idempotent et ne rétablit pas les
anciennes valeurs par-dessus une édition utilisateur.

Les modèles vendus ou liés à une vente sont en **lecture seule** : champs,
affectations, carrosseries montées et nouvelles compatibilités protégés. Les
informations historiques et snapshots existants sont consultables. Aucune action
Vendre/Restaurer, aucune commande `SellVehicle`/`RestoreSale`, aucun nouveau journal
de vente : l'étape 4 n'est pas commencée. Les références d'inventaire restent des
fiches courantes ; les snapshots de vente conservent leurs propres données figées.

## Architecture et limites

`collection/` contient DAO, repository, snapshot, règles de présentation et
ViewModel. `ui/collection/` contient navigation, listes, détails et formulaires.
États chargement, vide, lecture impossible/réessayer et erreur de sauvegarde.
Les sauvegardes doubles sont bloquées pendant une opération ; une erreur conserve
le formulaire. Les valeurs inconnues ne deviennent ni zéro, ni faux, ni « Origine »
dans la base. Le mode RTR inconnu/non personnalisé reste masqué jusqu'à une
activation explicite, sans changer les données.

L'interface gère les quantités agrégées par référence ; les unités sérialisées
existantes et leurs liens sont conservés/protégés mais n'ont pas encore d'éditeur
dédié. La quantité d'une affectation identifiée reste 1 ; elle ne peut pas être
convertie silencieusement en quantité agrégée. Il n'y a pas de suppression de fiches ni corbeille (lot 5), ni Firebase.
La provenance d'origine des carrosseries importées est immuable ; les nouvelles
carrosseries sont ajoutées sans provenance d'origine supposée.

## Vérification

Depuis `android/` :

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

Les tests Room/Robolectric couvrent la lecture des 25 modèles, édition avec UUID et
originaux préservés, fermeture/réouverture, suggestions de châssis par marque,
recherche/filtres, RTR et conservation de l'électronique, radio partagée et
mémoires, servos, stock insuffisant, réaffectation/rollback, retrait/historique,
compatibilités batteries, carrosseries d'origine, archives et éditeurs périmés.
Les tests d'import et migrations antérieurs restent exécutés.

Un test Compose JVM exécute la navigation depuis l'accueil, recherche et ouverture
d'un modèle importé, modification annulée puis enregistrée, filtre Servo et
consultation du modèle utilisateur. Ce test est exécuté sous Robolectric, pas sur
un appareil réel. Les tests instrumentés Android sont compilés ; leur exécution
sur appareil/émulateur et la validation visuelle restent distinctes.

La CI existante compile et publie l'APK debug. Le workflow et l'application web
ne sont pas modifiés. Le travail est poussé uniquement sur
`feat/android-collection-issue-3` pour mettre à jour la PR #13, sans nouvelle PR
ni fusion automatique.

Résultat local : **41 tests JVM exécutés, 0 échec, 0 ignoré**, dont un parcours
Compose JVM et 14 tests du dépôt de collection ; les 26 tests antérieurs restent
inclus. Compilation APK debug, lint et compilation des tests instrumentés réussis.
Aucun appareil/émulateur n'a été utilisé. Les avertissements lint antérieurs
(sauvegarde, convention v26, monochrome, suggestion KTX) restent hors périmètre.
