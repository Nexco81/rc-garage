# Icône officielle Android — issue #10

Le PNG fourni par le propriétaire dans le commit `96e438a` a été déplacé, sans
modification de ses octets, vers :

`app/src/main/res/drawable-nodpi/rc_garage_icon_artwork.png`

- PNG RGB, 1254 × 1254 pixels.
- SHA-256 : `3fff8cc0a7e67919197efffe8569c245ce4d12d2e6d8476f48db169c173c4728`.
- Aucun dessin régénéré, détourage, recadrage, recoloration ou filtre artistique.

## Ressources

Les icônes adaptatives `mipmap-anydpi-v26/ic_launcher.xml` et
`ic_launcher_round.xml` partagent le même premier plan et un fond noir constant.
Le manifeste déclare `android:icon` et `android:roundIcon`. Aucun thème clair/sombre
ne remplace ou ne teinte le dessin.

Le premier plan référence directement le PNG original `nodpi` avec un inset de
31/108 sur chaque bord. Cela place l'image entière dans un carré de 46 dp, centré
sur la couche adaptative de 108 dp. Sa diagonale, environ 65,05 dp, tient dans le
cercle de sécurité de 66 dp. Cette marge conservatrice garde aussi le cadre orange
entier, y compris sous un masque circulaire et pendant les déplacements de couche
prévus par Android. Le carré visible standard mesure 72 dp.

Les PNG de repli mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi mesurent respectivement
48/72/96/144/192 px, avec la même proportion de dessin, un fond carré arrondi et des variantes rondes.
Ils sont dérivés par réduction proportionnelle Lanczos et ajout de marge, sans
retouche du dessin. Ils servent aux consommateurs de ressources bitmap ; le
minSdk de l'application reste 31, donc les Android pris en charge utilisent
normalement l'icône adaptative.

La variante monochrome Android 13+ est volontairement absente : une conversion
monochrome modifierait les couleurs orange/noir/blanc validées. Les launchers
peuvent ainsi continuer à afficher l'icône officielle en couleur.

## Reproduction et revue

Avec Python 3 et Pillow (génération effectuée avec Pillow 13.0.0) :

```sh
python3 android/tools/generate_launcher_icons.py
```

Le script vérifie l'empreinte du fichier source avant et après génération. Il
produit les dix PNG de repli et `APP_ICON_MASK_PREVIEW.png`. Ce dernier montre les
rendus bitmap carré arrondi/rond sur fond clair/sombre ; il s'agit d'une **simulation pour
revue**, pas d'une capture sur appareil. Les ressources XML adaptatives sont
également chargées par `LauncherIconTest` sous Robolectric API 32.

Depuis `android/` :

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

La CI existante conserve ses vérifications et publie l'APK debug en artefact
`rc-garage-debug-apk`. Aucun workflow, fichier web ou code métier n'est modifié.
La tâche reste limitée à l'icône ; l'étape 3 n'est pas commencée.

## Limites et avertissements attendus

Aucun émulateur/appareil n'est disponible dans cet environnement : l'affichage
sur un vrai launcher et dans les paramètres reste à vérifier sur appareil. Le
chargement des ressources est testé sous Robolectric, la présence dans l'APK
est inspectée et les tests instrumentés existants sont compilés.

Lint peut signaler `ObsoleteSdkInt` pour le dossier v26 demandé par l'issue (la
convention explicite est conservée malgré minSdk 31), et `MonochromeLauncherIcon`
pour les deux ressources adaptatives. Ces choix sont intentionnels, documentés,
et aucun contrôle lint n'est désactivé. Les avertissements antérieurs sur les
règles de sauvegarde et la suggestion KTX restent hors périmètre.
