# Étape 4 — Vente, historique et restauration transactionnelle

## Mission Codex
Implémenter l'issue GitHub « Android 04 — Vente, historique et restauration transactionnelle » sur la branche existante `feat/android-sales-history-issue-4` (base `android-development`). Ouvrir/mettre à jour **uniquement la PR #14** si elle existe, sinon la PR existante indiquée par GitHub. Ne jamais fusionner automatiquement. Ne modifier que `android/` ; préserver la web app, l'icône, l'import JSON et les 25 modèles importés.

## Comportements métier à couvrir
1. Vente totale d'un modèle : conserver sa fiche dans l'historique (ne pas effacer), enregistrer date/prix facultatifs, snapshot immuable des équipements affectés, carrosseries et batterie si pertinentes. Ne garder que la photo principale dans le snapshot historique de vente, sans copier les galeries complètes.
2. Vente partielle / démontage : sélectionner individuellement les composants inclus dans la vente et ceux qui reviennent en stock ; ne pas présumer qu'un équipement suit le modèle. Mise à jour atomique des affectations et quantités physiques ; quantité inconnue reste inconnue.
3. Radios multi-modèles : associations non consommatrices de stock ; une radio peut rester liée à plusieurs modèles. Une vente ne doit pas faire disparaître les associations des autres modèles.
4. RTR d'origine : conserver l'indicateur RTR et « Origine » sans créer de faux équipements ; respecter la personnalisation électronique existante et les valeurs masquées.
5. Annulation/restauration : proposer une prévisualisation des effets, détecter les conflits composant par composant (ex. équipement réaffecté, stock insuffisant, modèle déjà actif, carrosserie indisponible). L'utilisateur choisit explicitement une résolution pour chaque conflit ; ne jamais écraser silencieusement les affectations existantes. Appliquer en transaction Room, ou ne rien changer si une vérification échoue (rollback). Préserver les snapshots et la traçabilité des opérations.
6. Vente d'un équipement isolé : stock et associations cohérents ; empêcher quantité négative, double retour ou double déduction. La quantité des éléments sérialisés doit rester cohérente avec les unités identifiées. Prévoir tests d'idempotence.
7. Historique consultable et détails lisibles ; actions de vente/annulation avec confirmation et messages explicites. Aucune suppression destructive de données.

## Contraintes techniques
- Examiner le schéma Room v3, les DAO/repository de l'étape 3 et les migrations existantes avant tout changement ; migrations **non destructrices** obligatoires et tests de migration/upgrade.
- Ne pas supposer la structure des données importées ; ne pas réécrire le JSON historique.
- Protéger les transactions contre les opérations concurrentes et les doubles clics.
- Écrire des tests JVM/Room pour vente complète, vente partielle, retour en stock, radio partagée, RTR, snapshot photo principale, conflits multiples résolus individuellement, annulation atomique, rollback sur échec, idempotence, stock inconnu et migration.
- Compilation, lint, tests et deux workflows CI ; produire APK debug et documenter les limitations non traitées.

## Livrable
Code, UI Compose, tests et documentation sur la branche de l'étape 4. Mettre à jour la PR existante vers `android-development`, sans la fusionner. Ne pas démarrer l'étape 5. Si une règle métier reste ambiguë, adopter un comportement conservateur qui empêche toute perte de données et l'indiquer clairement.
