# Le prompt Salon

Un accueil de TV à la française, sans pistage : le programme TV du soir, « À voir cette semaine » et une
recherche sur toutes vos plateformes (Netflix, Canal+, Prime Video, Disney+, TF1+, M6+, Paramount+, Apple TV)
avec **Regarder sur…** qui ouvre le titre directement dans l'appli, les radios françaises, la météo, une photo de
famille au centre, et des réglages qu'on fait depuis son téléphone par QR code.

Il s'installe par-dessus [cleanyour.tv](https://cleanyour.tv) : le même lanceur léger et hors ligne, avec en plus
les panneaux Télé et Radios, Météo et « À voir » sur l'accueil, et l'add-on **Salon** qui les alimente. Une
démonstration filmée sur une vraie TV : [salon-demo.mp4](https://raw.githubusercontent.com/anhost77/cleanyourtv/pr-assets/addon-salon/salon-demo.mp4).

**Ce qu'il faut :**
- une TV **Android TV ou Google TV** (Android 8 ou plus récent). Pas Samsung (Tizen), LG (webOS), Roku ni Fire TV :
  pour celles-là, https://cleanyour.tv/not-android coupe le pistage avec la télécommande ;
- un ordinateur sur le **même Wi-Fi** que la TV, avec [Claude Code](https://claude.com/claude-code) (ou un autre
  assistant qui a accès à un terminal) ;
- de préférence la France : le programme, les plateformes et les radios sont français (d'autres pays sont
  possibles, l'assistant vous dira ce qui marche) ;
- facultatif : un compte gratuit [TMDB](https://www.themoviedb.org/) pour « À voir », la recherche et les fiches.

**Avant de commencer, sur la TV :** Paramètres → Préférences de l'appareil → À propos → appuyez 7 fois sur
« Build », revenez en arrière → Options pour les développeurs → activez le **débogage USB** (et le débogage
réseau / ADB si la TV le propose). Laissez la TV allumée.

Collez ensuite tout le texte entre les deux traits dans l'assistant.

---

Je veux installer « Salon » sur ma TV Android : le lanceur de cleanyour.tv avec les panneaux de l'accueil, l'add-on
Salon (programme TV, plateformes, radios, météo, réglages) et, si je le veux, l'Espace parents (navigateur).
Mon modèle de TV : <MODÈLE ICI>. Travaille sur la vraie TV, étape par étape, vérifie chaque étape par une capture
d'écran prise sur la TV, et demande-moi avant tout ce qui dépend de mes préférences. Réponds-moi en français.

Le code
- Clone ce dépôt sur sa branche Salon : `git clone -b salon https://github.com/anhost77/cleanyourtv` et travaille
  dedans. Lis d'abord `PROMPT.md` (le prompt officiel de cleanyour.tv) et `AGENTS.md` : **toutes leurs règles de
  sécurité s'appliquent ici telles quelles** (connexion, sauvegarde, allègement, prise de l'accueil, fermeture
  d'ADB). Ce prompt n'ajoute que ce qui est propre à Salon ; en cas de doute, c'est `PROMPT.md` qui fait foi.
- Construis tout depuis les sources de ce dépôt (`launcher/`, `addons/salon/`, `addons/parents/`), avec les outils
  de `tools/`. N'installe aucun APK venu d'ailleurs.

Vérifier avant d'installer (et me dire clairement ce qui passe ou non)
- Connecte-toi comme le dit `PROMPT.md` (ADB sur le port 5555, ou le débogage sans fil). Si la TV n'est pas une
  Android TV / Google TV, arrête-toi et renvoie-moi vers https://cleanyour.tv/not-android.
- Niveau d'Android : `getprop ro.build.version.sdk` doit valoir au moins 26 (le `minSdk` du lanceur et de Salon).
  En dessous, dis-moi que Salon ne s'installe pas sur cette TV ; ne baisse pas le `minSdk`.
- Installation d'applis : vérifie que la TV accepte une appli installée depuis l'ordinateur, en commençant par
  Salon (une appli ordinaire, sans risque pour l'accueil). Un refus `INSTALL_FAILED_VERIFICATION_FAILURE`, ou une
  TV qui n'accepte un accueil que signé par le fabricant, veut dire : pas de lanceur ici. Dis-le et propose
  seulement l'allègement de `PROMPT.md`.
- Mémoire : lis `MemTotal` dans `/proc/meminfo`. Avec 1 Go, préviens-moi que les panneaux de l'accueil pèsent un peu
  et propose de les garder quand même (ils se rafraîchissent seulement quand l'accueil est à l'écran).
- Écran : `wm size` et `wm density`. L'accueil est dessiné pour du 16:9 ; sur une autre forme, fais une capture et
  dis-moi si les panneaux tiennent.
- Pays et applis : demande-moi si je suis en France. Liste les applis installées qui comptent pour Salon (Free TV
  `net.oqee.androidtv.store`, myCANAL `com.canal.android.canal`, Netflix `com.netflix.ninja`, Prime Video, Disney+,
  TF1+ `fr.tf1.mytf1`, M6+ `fr.m6.m6replay`, Apple TV, Paramount+ `com.cbs.ca`, YouTube) et dis-moi ce que chacune
  apportera (« Regarder » sur une chaîne, les fiches des titres). Hors de France, explique que le programme TV, les
  liens des plateformes et les radios sont français, et demande si je veux continuer.
- Réseau : la TV doit joindre `xmltvfr.fr`, `api.themoviedb.org` et `api.open-meteo.com`. Si un filtre DNS ou un
  pare-feu les bloque, dis-le-moi.

Sauvegarder et alléger
- Fais la sauvegarde et le script de restauration de `PROMPT.md` avant le moindre changement.
- Propose-moi l'allègement de `PROMPT.md` (avec sa liste de questions sur les applis que j'utilise) ; il est
  facultatif pour Salon.

Construire et installer
- Le lanceur : dans `launcher/app/src/main/res/values/config.xml`, garde `tv_panel` et `weather_panel` sur
  `io.github.anhost77.salon` (ce sont les panneaux de l'accueil). Installe-le avec
  `launcher/build-and-install.sh` (ou `.ps1`), qui garde les protections de la prise de l'accueil ; respecte les
  règles de `PROMPT.md` sur le lanceur d'origine, setupwraith et les cartes CVTE.
- Le thème : copie `themes/salon/` dans un dossier à moi hors du dépôt (par exemple `work/salon/`), demande-moi
  les applis de la rangée du bas et leur ordre (`dock_apps`, 8 au plus sur une seule rangée) et les applis du
  fabricant à cacher (`hidden_apps`), puis pousse-le avec `tools/theme.sh <ce dossier>`.
- Salon : `cd addons/salon && ./gradlew assembleRelease`, puis installe l'APK. Ouvre l'accueil : les panneaux
  Télé, Météo et « À voir » doivent apparaître (« À voir » seulement avec une clé TMDB).
- L'Espace parents (globe en haut de l'accueil) : demande-moi si je le veux. Si oui, construis `addons/parents`,
  installe-le et ajoute `fr.maison.parents` à `top_bar_apps` dans le thème. Il me demandera de choisir un code.

Régler Salon (avec moi)
- La ville de la météo : ouvre la tuile Météo (ou Réglages de l'accueil > Ville) et laisse-moi la taper.
- La clé TMDB : explique-moi comment créer un compte gratuit sur themoviedb.org et copier le « jeton d'accès en
  lecture » (Paramètres > API). Je la saisirai moi-même depuis le téléphone : roue dentée de l'accueil >
  Réglages de l'accueil > Depuis le téléphone > QR code. Ne la recopie jamais dans un fichier du dépôt.
- La photo de l'accueil : même page du téléphone. Elle reste sur la TV ; ne l'envoie nulle part, ne la mets dans
  aucun fichier du dépôt ni aucune capture que tu partages.
- « Liens directs vers les fiches (via JustWatch) » (Réglages > Plateformes) est désactivé par défaut. Explique-moi
  qu'il passe par l'API non officielle du site JustWatch, et que l'activer ouvre directement les fiches de myCANAL,
  M6+, Apple TV et Paramount+ au lieu de leur accueil. C'est mon choix.
- Les plateformes, chaînes et radios à afficher : Réglages de l'accueil, une ligne par élément (Oui / Non).

Vérifier
- Comme dans `PROMPT.md` : injecte les touches de la télécommande et regarde les captures. Parcours l'accueil de
  haut en bas (barre du haut, onglets CE SOIR À LA TÉLÉ / RADIOS, Météo, « Tout voir », la rangée d'applis), puis
  le programme TV, « À voir cette semaine » et une fiche. Haut et Bas doivent toujours aller à ce qui est juste
  au-dessus ou en dessous ; corrige ce qui ne suit pas l'écran.
- Netflix et la plupart des applis de streaming donnent des captures vides : c'est normal. Pour un lien
  « Regarder sur… », vérifie l'appli ouverte avec `dumpsys activity activities | grep -i ResumedActivity` et
  demande-moi ce que je vois.

Terminer
- Referme ADB exactement comme le dit `PROMPT.md` (débogage réseau et sans fil coupés, débogage USB aussi sauf si je
  demande de le garder, `tools/adb-closed.py <ip de la TV>` qui doit dire « closed », redémarrage, nouvelle
  vérification).
- Laisse-moi un `LISEZMOI.md` en français : ce qui a été installé et désactivé, comment tout remettre comme avant,
  comment changer la rangée d'applis, la photo, la ville et la clé TMDB, et comment rouvrir le débogage pour une
  prochaine modification.

---

Comptez trois ou quatre questions (les applis que vous utilisez, la rangée du bas, l'Espace parents, la ville).
Tout est réversible. Salon est un projet personnel construit sur cleanyour.tv : il n'est ni relu ni maintenu par
le projet officiel. Questions et problèmes : https://github.com/anhost77/cleanyourtv/issues
