# A segunda semana

<!-- languages -->
[English](the-second-week.md) · [Español](the-second-week.es.md) · [Français](the-second-week.fr.md) · [Deutsch](the-second-week.de.md) · [Русский](the-second-week.ru.md) · [Українська](the-second-week.uk.md) · [Polski](the-second-week.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](the-second-week.id.md) · [Filipino](the-second-week.tl.md) · [Tiếng Việt](the-second-week.vi.md) · [简体中文](the-second-week.zh.md) · [हिन्दी](the-second-week.hi.md) · [עברית](the-second-week.he.md) · [العربية](the-second-week.ar.md)
<!-- /languages -->

*Fazer commit, revisar, resolver, propor — sem sair para outra ferramenta.*

A primeira hora é abrir um projeto e executá-lo. A segunda semana é tudo
o que cerca o código: vinte commits por dia, um diff para ler antes de
cada um, um conflito depois de um pull, um pull request depois de um push,
um stack trace para rastrear, um README para manter honesto. Este passeio
é uma sessão só, num repositório git que você já tem, e cada passo é algo
que você vai fazer de novo amanhã.

## 1. Faça do NMOX Studio o editor do git

**Faça:** Equipe ▸ **Usar o NMOX Studio com o Git…**

**Veja:** as seis configurações globais do git que fazem do NMOX Studio o
editor, a difftool e a mergetool do git, cada uma ao lado do valor que tem
**agora**, para que nada seja substituído sem você ver. **Aplicar** as
define (**Fechar** é o botão padrão, porque isto grava a sua configuração
global do git); **Copiar comandos** põe em vez disso as linhas
`git config` na área de transferência. Quando o git já usa o NMOX Studio,
o diálogo diz isso e não oferece Aplicar.

As mesmas linhas, se você preferir um terminal:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

## 2. Faça commit

**Faça:** altere um arquivo e depois, num terminal:

```bash
git commit -a
```

**Veja:** a mensagem de commit abre no NMOX Studio, e a linha de status diz
que um terminal está esperando por ela. As linhas `#` do git são
comentários; só o que você escreve passa pelo corretor ortográfico; uma
linha de resumo com mais de 72 caracteres, onde as próprias ferramentas do
git a cortam, recebe um aviso depois do 72º. Salve, feche a aba e o commit
acontece — o terminal estava esperando até você fazer isso. Sair da IDE
com a mensagem ainda aberta também a devolve ao git, com o que estiver
salvo.

`git rebase -i` abre sua lista do mesmo jeito: cada comando e cada commit
destacados, e **Alternar comentário** descarta uma linha sem apagá-la.

## 3. Saiba onde você está

**Veja:** o **indicador ⎇** na linha de status — `⎇ main ±3 ↑2 ↓1` é o seu
branch, três arquivos alterados, dois commits para enviar e um para trazer
(as setas só aparecem quando há algo para enviar ou trazer). O menu dele
começa com **Trocar de branch…**, **Fazer commit…**, **Fazer pull…** e **Fazer push…**.

**Faça:** ponha o cursor em qualquer linha de um arquivo versionado.

**Veja:** ao lado do indicador, quem mudou essa linha por último, há
quanto tempo e por quê: `Ada Lovelace, há 3 dias · Fix the parser`. Uma
linha que você ainda não commitou diz isso, e um arquivo com alterações
não salvas diz isso em vez de nomear o autor errado. Clique na nota para
ver as anotações do arquivo inteiro, o commit no GitHub ou o ID dele;
**Exibir ▸ Autoria da linha** a desliga.

## 4. Revise um diff

**Faça:**

```bash
git difftool
```

**Veja:** cada arquivo alterado lado a lado na visão de diferenças do NMOX
Studio, com **Diferença anterior / Próxima diferença** e “Diferença 2 de 5”
acima. Um arquivo adicionado ou excluído mostra o lado ausente como um
painel vazio (“nenhum arquivo”); um arquivo binário aparece como binário, e
a barra diz se dois binários diferem. Feche a aba e o git passa para o
próximo arquivo.

## 5. Resolva um conflito

**Faça:** faça merge de um branch que entra em conflito e depois:

```bash
git mergetool
```

**Veja:** o arquivo em conflito no editor, com os lados atual e de entrada
tingidos, e um aviso em cada linha `<<<<<<<`. Ponha o cursor nela e
pressione ⌘. (Alt+Enter nos outros sistemas), ou use **Código-fonte ▸
Corrigir código…**: **Aceitar alteração atual**, **Aceitar alteração de
entrada** ou **Aceitar ambas as alterações**, cada uma uma única edição
que pode ser desfeita. Um bloco que mudou desde a oferta é recusado em vez
de adivinhado. Salve, feche a aba e responda ao git.

## 6. Proponha

**Faça:** faça push e depois Equipe ▸ **Novo pull request no GitHub**
(também no menu do indicador).

**Veja:** a própria página New Pull Request do GitHub para o seu branch, no
seu navegador, onde você já está conectado. A partir de um editor,
**Editar ▸ Abrir no GitHub** e **Copiar link do GitHub** dão a linha ou as
linhas em que você está; na árvore do Estúdio de projeto, dão um arquivo
ou uma pasta.

## 7. Persiga uma falha

**Faça:** rode seus testes no Terminal (⌃\`) até que um falhe.

**Veja:** uma localização na saída — `src/app.ts:42:7`, um quadro de pilha
`(/abs/app.js:10:5)`, `--> src/main.rs:3:5`, `File "x.py", line 12` — abre
naquela linha e coluna com ⌘-clique (Ctrl-clique no Windows e no Linux).
Uma URL ou `localhost:3000` nunca é um link, e um caminho que não existe é
recusado pelo nome em vez de adivinhado.

## 8. Mantenha o README honesto

**Faça:** Ferramentas ▸ **Verificar links do Markdown…**

**Veja:** cada link relativo e cada imagem no Markdown do projeto
verificados do jeito que o GitHub os mostra — o arquivo precisa existir, e
um `#heading` precisa ser um título desse arquivo. Um link morto é um erro,
um título ausente é um aviso, ambos como sublinhados ondulados e em Itens
de ação, com uma frase na linha de status. Nada sai da sua máquina: um
link com esquema não é verificado.

## 9. Entregue a um agente

**Faça:** Ferramentas ▸ **Agent Port (MCP)…**, marque **Manter este
endereço e este token**, depois **Copiar para o Claude Code**, e execute
uma vez a linha copiada.

**Veja:** um agente que consegue ler o que a IDE sabe — o projeto mirado,
o que está servindo e rodando, o que você está editando, a última falha —
e que ainda se conecta amanhã, porque o token fica guardado no chaveiro do
seu sistema e a porta é reutilizada. A porta continua somente leitura por
construção. Desmarcar **Manter este endereço e este token** apaga a
entrada do chaveiro.

## O que você fez

Você escreveu uma mensagem de commit, leu um diff, resolveu um conflito,
abriu um pull request, descobriu quem escreveu uma linha, seguiu um stack
trace e verificou um README — tudo na janela em que você já estava. Nada
disso substituiu o git: cada passo é do próprio git, aberto onde você
trabalha.
