# Tutoriel : KVASIR — l’IA qui explique les erreurs

<!-- languages -->
[English](kvasir.md) · [Español](kvasir.es.md) · **Français** · [Deutsch](kvasir.de.md) · [Русский](kvasir.ru.md) · [Українська](kvasir.uk.md) · [Polski](kvasir.pl.md) · [Português (Brasil)](kvasir.pt.md) · [Bahasa Indonesia](kvasir.id.md) · [Filipino](kvasir.tl.md) · [Tiếng Việt](kvasir.vi.md) · [简体中文](kvasir.zh.md) · [हिन्दी](kvasir.hi.md) · [עברית](kvasir.he.md) · [العربية](kvasir.ar.md)
<!-- /languages -->

KVASIR est un appareil du rack qui lit votre dernière exécution échouée et
demande à votre IA — Claude, ChatGPT ou Gemini — ce qui a mal tourné.
C’est l’assistance par IA à la manière du rack : un bouton, une porte de
consentement claire et un afficheur honnête — aucun fichier du projet ni
secret n’est envoyé, seulement le contexte borné de l’échec.

![KVASIR expliquant une exécution réellement échouée : le diagnostic consenti sur la façade et les étapes complètes de correction dans la visionneuse](../images/kvasir-explain.png)

## Avant de commencer

Il vous faut une clé d’API chez l’un des trois fournisseurs que parle
KVASIR : Anthropic (Claude), OpenAI (ChatGPT) ou Google (Gemini). Pressez
**KEY…** sur la façade pour choisir le fournisseur et ranger sa clé dans
le trousseau du système, ou exportez la variable d’environnement du
fournisseur — `ANTHROPIC_API_KEY` / `CLAUDE_API_KEY`, `OPENAI_API_KEY` /
`CHATGPT_API_KEY`, ou `GEMINI_API_KEY` / `GOOGLE_API_KEY`. Le choix du
fournisseur vaut pour tous les visages de KVASIR et se trouve aussi dans
Options ▸ Rack et cloud.

## Étapes

1. **Provoquez un échec.** Lancez quelque chose qui échoue — une
   construction avec une erreur de syntaxe, un test qui lève une
   exception. L’enregistreur de vol du rack capture la commande, le code
   de sortie et jusqu’à cinq lignes d’erreur échantillonnées.

2. **Montez KVASIR** depuis la palette (catégorie OBSERVE) et pressez
   **EXPLAIN**.

3. **Donnez votre consentement (la première fois).** KVASIR a sa propre
   boîte de dialogue de consentement unique, par fournisseur, qui nomme
   l’éditeur qui reçoit les données et détaille exactement ce qui quitte
   votre machine : la commande en échec, son code de sortie, au plus
   5 lignes d’erreur, le nom de l’appareil et le nom du projet — et rien
   d’autre (ni source, ni environnement, ni secrets). La confiance de
   l’espace de travail garde l’*exécution* de code ; ce flux de données
   sortant a sa propre porte.

4. **Lisez le verdict.** Un court diagnostic s’affiche sur l’afficheur
   multiligne ; l’explication complète s’ouvre dans une fenêtre. Le bouton
   **MODEL** choisit FAST (par défaut) ou DEEP — Haiku / Sonnet,
   GPT-5 mini / GPT-5, ou Gemini Flash / Pro, selon le fournisseur choisi.

## Ce que vous venez d’apprendre

- KVASIR ne coûte rien au démarrage et ne fait aucun appel réseau sans
  la pression du bouton — la porte de la clé et celle du consentement
  sont toutes deux imposées.
- La clé ne voyage que dans l’en-tête d’authentification du fournisseur
  (`x-api-key`, `Authorization: Bearer`, `x-goog-api-key`) — jamais dans
  une URL, un corps de requête ou un journal.
- Les clés ne passent jamais d’un fournisseur à l’autre, et le
  consentement vaut par fournisseur : un oui pour Anthropic n’est pas un
  oui pour Google ou OpenAI.
- La dégradation est honnête : pas de clé, pas de consentement, rien à
  expliquer, hors ligne ou refus — chacun affiche un message clair.

## Et ensuite

- Câblez-le en mains libres : un câble `VERITAS FAIL → KVASIR EXPLAIN`
  explique automatiquement une série de tests échouée (le chemin par câble
  ne demande jamais rien et se limite à un appel toutes les 30 s) ; sa
  sortie OUT alimente MONITOR/PHOSPHOR.
