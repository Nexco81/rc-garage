# Correction de l'import historique — quantités et radios

## Règle métier validée

La quantité physique d'une radio et ses associations aux modèles RC sont deux
informations indépendantes. Une Flysky G7P peut avoir trois associations/mémoires
sans que cela implique trois radios possédées. Ces associations ne consomment pas
le stock. Une Flysky GT5 ou G7P sans `quantity` garde `ownedQuantity = null` ; ni 1
ni le nombre de modèles ne sont déduits. Une quantité explicite, y compris zéro,
est conservée. Les autres composants gardent les contrôles de stock habituels.
Les identités radio, rôles, liens récepteurs/radios et mémoires restent validés.

La correction s'applique à toutes les radios, sans liste de marques particulière.
La lecture de disponibilité d'une radio renvoie sa quantité physique inchangée
ou null, indépendamment du nombre d'associations. Une unité physique identifiée
reste soumise à sa propre référence et à la quantité physique connue : les
associations seules ne créent aucune unité de stock.

## Lecture et conservation

- `quantity` accepte les nombres JSON entiers non négatifs et les chaînes de
  chiffres ASCII, avec espaces périphériques éventuels : `"1"`, `"4"`, `"10"`.
  Conversion contrôlée en Int, entre 0 et 2147483647. Les fractions, nombres
  négatifs, booléens, dépassements, chaînes vides, signes et notations scientifiques
  textuelles sont rejetés. Un champ absent/null reste inconnu ; un texte invalide
  ne devient jamais silencieusement zéro ou inconnu.
- Les spécifications sources restent stockées telles qu'interprétées précédemment.
  Pour la déduplication seulement, la quantité validée est comparée numériquement :
  `"4"` et `4` ne créent ni doublon ni conflit artificiel. Aucune spécification
  existante n'est réécrite. Toute autre différence métier reste un conflit bloquant.
- Les UUID existants et la conversion déterministe des anciens identifiants sont
  inchangés, comme les liens, statuts, données RTR et historiques inconnus. Les notes
  sont conservées littéralement, y compris espaces et retours à la ligne.
- Les `boxBody` explicitement activées conservent leur provenance d'origine et leur
  véhicule ; les `bodyStock` gardent leurs propres identifiants, affectations et
  provenance inconnue lorsqu'elle n'est pas renseignée. L'aperçu affiche désormais
  séparément les nombres de carrosseries d'origine et indépendantes, sans inventer
  de carrosserie de boîte lorsqu'elle n'est pas activée.
- L'aperçu et son abandon n'écrivent rien. La confirmation explicite reste obligatoire.
  L'import revalide les octets puis effectue une transaction unique ; toute erreur
  ou tout conflit annule les insertions. Les copies originales exactes, empreintes
  et journaux restent conservés/immuables. Aucun moteur ou composant RTR n'est inventé.

## Migration sans changement de données

La base passe de v2 à **v3** pour remplacer les quatre triggers de stock concernés
(`equipment_stock_insert/update`, `assignments_stock_insert/update`). La migration
ne fait aucun UPDATE/DELETE/INSERT sur les lignes métier : elle remplace des règles
SQL. Les schémas v1/v2 restent figés et v3 est exporté. Les installations neuves et
les bases migrées utilisent les mêmes règles. Pas de migration destructive.

Aucune sauvegarde personnelle n'a été importée, réécrite ou corrigée automatiquement.
Les tests n'utilisent que des données synthétiques. Un réimport qui contredit une
quantité ou une note déjà enregistrée reste bloqué : il ne répare pas implicitement
les anciennes données, et une telle correction exigerait une validation séparée.

## Vérifications

Depuis `android/` :

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

Les nouveaux tests Room/Robolectric reproduisent G7P + trois modèles/trois mémoires,
GT5 sans quantité, radio à quantité connue ou zéro, absence de consommation,
quantités textuelles et invalides, déduplication texte/nombre sans réécriture,
carrosseries distinctes, fidélité UUID/notes/RTR/historique et rollback avec un
moteur déjà alloué. Un test ouvre le schéma v2 figé avec les anciens triggers,
reproduit le refus d'une radio inconnue, puis migre vers v3 : toutes les lignes de
toutes les tables métier sont identiques juste après migration et les nouvelles
règles fonctionnent. La surallocation des composants non radio reste refusée.

Les tests Compose instrumentés sont compilés ; leur exécution sur appareil ou
émulateur reste distincte des tests JVM exécutés sous Robolectric.

Résultat local : **26 tests JVM exécutés, 0 échec, 0 ignoré** ; compilation APK, lint et compilation des tests instrumentés réussies. Aucun appareil/émulateur n’a été utilisé. Les avertissements lint existants restent hors périmètre.
