# Tutoriel : tout expliquer avec KVASIR

<!-- languages -->
[English](explain-anything.md) · [Español](explain-anything.es.md) · **Français** · [Deutsch](explain-anything.de.md) · [Русский](explain-anything.ru.md) · [Українська](explain-anything.uk.md) · [Polski](explain-anything.pl.md) · [Português (Brasil)](explain-anything.pt.md) · [Bahasa Indonesia](explain-anything.id.md) · [Filipino](explain-anything.tl.md) · [Tiếng Việt](explain-anything.vi.md) · [简体中文](explain-anything.zh.md) · [हिन्दी](explain-anything.hi.md) · [עברית](explain-anything.he.md) · [العربية](explain-anything.ar.md)
<!-- /languages -->

KVASIR a commencé comme un appareil du rack qui explique les exécutions
échouées. Il atteint maintenant quatre endroits — le rack, l’éditeur, le
Studio d’API et le Studio de bases de données — et chaque visage suit les
trois mêmes lois : **vous voyez exactement ce qui quitterait votre machine
avant que quoi que ce soit ne parte**, **chaque surface gagne son propre
consentement** (dire oui pour les erreurs de construction n’autorise jamais
l’envoi de code ou de SQL), et **les secrets ne peuvent pas voyager avec,
par construction** (la divulgation est assemblée par le studio à qui
appartiennent les données, les en-têtes d’identification retirés et les
mots de passe hors de portée).

![KVASIR expliquant une exécution réellement échouée](../images/kvasir-explain.png)

## Avant de commencer

Une seule clé couvre les quatre visages — chez le fournisseur de votre
choix : Claude (Anthropic), ChatGPT (OpenAI) ou Gemini (Google). Pressez
**KEY…** sur la façade de KVASIR pour choisir le fournisseur et ranger sa
clé dans le trousseau de votre système, ou exportez `ANTHROPIC_API_KEY`,
`OPENAI_API_KEY` ou `GEMINI_API_KEY`. Pas de clé, pas d’appel — chaque
visage le dit honnêtement.

## Les quatre visages

1. **Une exécution échouée (le rack).** Montez KVASIR, lancez quelque chose
   qui échoue, pressez **EXPLAIN**. Ce qui est envoyé : la commande, le code
   de sortie et jusqu’à cinq lignes d’erreur échantillonnées. Voyez [le
   tutoriel KVASIR](kvasir.fr.md) pour le parcours complet, y compris le
   câble qui explique en mains libres un échec de VERITAS.

2. **Votre code (l’éditeur).** Sélectionnez du code dans n’importe quel
   langage → clic droit → **Demander à KVASIR à propos de la sélection…**
   et tapez une question. Ce qui est envoyé : la sélection plafonnée, le
   nom du fichier et le langage — rien d’autre de votre projet. Ce visage
   a sa *propre* porte de consentement, parce que le consentement du flux
   des échecs promet explicitement que le code source ne quitte jamais la
   machine.

3. **Une réponse d’API (le Studio d’API).** Après un envoi, pressez
   **Expliquer…**. Ce qui est envoyé : la méthode, l’URL avec les valeurs
   de la chaîne de requête masquées, le statut, les en-têtes avec les
   identifiants retirés et comptés, et un corps plafonné. Utile dès qu’un
   401 ou un en-tête CORS étrange apparaît.

4. **Une erreur de base de données (le Studio de bases de données).** Une
   instruction échouée reçoit un bouton **Expliquer…** sous son message
   d’erreur. Ce qui est envoyé : le SQL que vous avez exécuté — *valeurs
   littérales comprises, et la ligne de consentement le dit*, parce que
   l’erreur porte en général sur un littéral — plus le message d’erreur et
   le type de moteur. Jamais la connexion, le mot de passe ni les lignes.

Chaque visage ouvre une fenêtre de conversation : posez des questions de
suivi, et le modèle voit tout l’historique de cet échange (plafonné à dix
échanges, ce que dit la transcription). Le choix **Fast/Deep**
(Haiku/Sonnet) est mémorisé, et fixé pour chaque conversation, pour que la
transcription ne mente jamais sur qui a répondu.

## Essayez en deux minutes

Le Studio de bases de données est le visage le plus rapide à montrer :
ouvrez ⌥⌘7, créez une connexion SQLite, lancez `SELECT * FROM user;` contre
une base dont la table s’appelle `users`, et pressez **Expliquer…** sur
l’erreur. Lisez la boîte de dialogue de consentement avant d’accepter —
c’est la promesse du produit, en une phrase.

## Ce que vous venez d’apprendre

- Quatre surfaces, une seule jointure : chaque studio assemble sa propre
  divulgation, et la boîte de dialogue de consentement la cite mot pour mot.
- Un refus est respecté en silence et entièrement — pas de fenêtre, pas
  d’appel.
- Un résultat appartient à l’espace de travail qui l’a produit : changer de
  projet efface les réponses et les onglets de résultats, si bien
  qu’Expliquer ne peut jamais divulguer les données d’un projet précédent.
