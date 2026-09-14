# Tutorial: espaços de aprendizado

<!-- languages -->
[English](learning-spaces.md) · [Español](learning-spaces.es.md) · [Français](learning-spaces.fr.md) · [Deutsch](learning-spaces.de.md) · [Русский](learning-spaces.ru.md) · [Українська](learning-spaces.uk.md) · [Polski](learning-spaces.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](learning-spaces.id.md) · [Filipino](learning-spaces.tl.md) · [Tiếng Việt](learning-spaces.vi.md) · [简体中文](learning-spaces.zh.md) · [हिन्दी](learning-spaces.hi.md) · [עברית](learning-spaces.he.md) · [العربية](learning-spaces.ar.md)
<!-- /languages -->

![O seletor de Novo espaço de aprendizado — busca nos tutoriais que já vêm com o produto, com a sondagem de disponibilidade dizendo de antemão se esta máquina tem a ferramenta do espaço](../images/tabs/learning-spaces.png)

Um espaço de aprendizado é um ambiente autocontido para aprender uma
linguagem, um framework ou uma biblioteca: o NMOX Studio gera o código de
exemplo, um tutorial guiado e um rack já cabeado com um **REPL de verdade
dentro do rack**, onde você digita. Há 93 que já vêm com o produto.

<!-- screenshot: a learning space open — sample code, the tutorial pane, and the REPL device with typed input -->

## Abrir

`Arquivo ▸ Novo espaço de aprendizado…` (o seletor lista todos os espaços
que já vêm com o produto).

## Passos

1. **Escolha um espaço.** Escolha um — Python, Rust, Solid, htmx, Solidity,
   Elm, um REPL de linguagem de sistemas, um espaço de E2E/Playwright etc. O
   seletor primeiro verifica se o interpretador ou a cadeia de ferramentas
   está disponível.

2. **Deixe gerar.** O NMOX Studio cria o espaço em
   `~/.nmox/learn/<slug>`: um exemplo mínimo que funciona e um tutorial que
   o percorre com você, apontando para o console ou dispositivo certo.

3. **Digite no REPL.** O rack já cabeado inclui um dispositivo **REPL**
   cujo botão ENGINE está ajustado para a linguagem do espaço (26 motores,
   cada um com as flags de modo interativo já preenchidas). Digite uma
   expressão e aperte Enter — a saída corre na tela do REPL. Falta o
   interpretador? O botão **INSTALL** o instala a partir do rack.

4. **Siga o tutorial.** Percorra os passos; o código de exemplo é real e
   executável, e o espaço é seu para modificar.

## O que você aprendeu

- Um espaço de aprendizado é um projeto completo + tutorial + rack cabeado,
  não só um trecho de código.
- O REPL é um processo interativo de verdade, não uma reprodução gravada.
- Você pode acrescentar os seus: ponha um `*.json` em
  `~/.nmox/learn-catalog.d/` e ele entra no seletor (veja
  [learning-spaces.md](../learning-spaces.md) para o esquema).

## Próximos passos

- Os espaços de framework (Astro/SvelteKit/Nuxt/Next) apontam para o
  console deles no rack (COSMOS/KINETIC/NIMBUS/NEXUS).
