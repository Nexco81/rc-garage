# Architecture Android — décision initiale

## Invariants métier
- Identifiants UUID stables pour modèles, équipements, carrosseries, batteries et opérations.
- Quantité possédée >= 0 ; quantité disponible = quantité possédée - allocations actives (hors radios partagées).
- Les composants RTR d'origine sont représentés par un état « Origine », jamais par une ligne fictive de stock.
- La marque et le modèle du véhicule sont obligatoires ; châssis suggérés uniquement pour la marque choisie.
- Équipements et carrosseries : marque en saisie libre avec suggestions.
- Carrosserie d'origine attachée au modèle tant que montée ; retrait vers inventaire indépendant.
- Ventes : journal immuable des composants et de leur sort ; photographie principale seulement en archive.
- Annulation : vérification des conflits puis transaction atomique, résolution composant par composant.
- Une archive historique sans détail de vente reste « inconnu », jamais « vendu complet » par défaut.
- Les photos et données privées sont isolées par UID Firebase ; aucune collection partagée.

## Modèle Room proposé
Vehicle(id, brand, name, chassis, category, scale, status, isRtr, electronicsCustomized, notes, primaryPhotoId, saleId, updatedAt)
Equipment(id, type, brand, reference, ownedQuantity, specsJson, updatedAt)
EquipmentAssignment(id, vehicleId, equipmentId, role, quantity, isActive)
RadioMemory(id, radioId, slot, vehicleId, receiverId)
BodyShell(id, originVehicleId?, currentVehicleId?, isOriginal, brand, name, condition, decoration, notes)
Battery(id, brand, reference, chemistry, capacityMah, cells, voltage, cRating, connector, ownedQuantity)
BatteryCompatibility(batteryId, vehicleId)
VehiclePhoto(id, vehicleId, uri, orderIndex, isPrimary)
Sale(id, vehicleId, createdAt, saleType, historyKnown, snapshotJson)
SaleLine(id, saleId, equipmentId?, equipmentSnapshotJson, role, disposition, quantity)
StockMovement(id, equipmentId, deltaOwned, reason, saleId?, timestamp)
SyncChange(id, entityType, entityId, revision, status, timestamp)

À détailler dans les entités Room et migrations ; `specsJson` n'est qu'une proposition initiale, à typer selon les catégories si nécessaire.

## Transactions
Le domaine fournit `SellVehicle` et `RestoreSale`. Chaque commande exécute les modifications Room dans une seule transaction et écrit les mouvements de stock. Les contrôles sont effectués avant le commit ; un conflit renvoie des options sans modifier les données.

## Synchronisation
Room reste utilisable sans Firebase. Une file d'opérations idempotentes et versionnées synchronise ensuite les changements par UID. Firestore ne doit pas permettre à un utilisateur de lire/écrire les données d'un autre. Ne pas coder de résolution « dernier arrivé gagne » pour les ventes et les stocks ; demander un arbitrage explicite.
