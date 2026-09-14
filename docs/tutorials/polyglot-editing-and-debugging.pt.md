# Tutorial: edição poliglota e depuração

<!-- languages -->
[English](polyglot-editing-and-debugging.md) · [Español](polyglot-editing-and-debugging.es.md) · [Français](polyglot-editing-and-debugging.fr.md) · [Deutsch](polyglot-editing-and-debugging.de.md) · [Русский](polyglot-editing-and-debugging.ru.md) · [Українська](polyglot-editing-and-debugging.uk.md) · [Polski](polyglot-editing-and-debugging.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](polyglot-editing-and-debugging.id.md) · [Filipino](polyglot-editing-and-debugging.tl.md) · [Tiếng Việt](polyglot-editing-and-debugging.vi.md) · [简体中文](polyglot-editing-and-debugging.zh.md) · [हिन्दी](polyglot-editing-and-debugging.hi.md) · [עברית](polyglot-editing-and-debugging.he.md) · [العربية](polyglot-editing-and-debugging.ar.md)
<!-- /languages -->

O NMOX Studio edita mais de 70 linguagens com realce de sintaxe de verdade,
o esquema no Navegador e a inteligência dos language servers — e depura
JavaScript/TypeScript (e o navegador) de fábrica, com pontos de
interrupção que param de fato. Este tutorial faz um app Node parar num
ponto de interrupção.

![Um ponto de interrupção de JavaScript atingido: execução pausada, a pilha de chamadas do Node e as variáveis vivas do V8](../images/debug-javascript.png)

## Antes de começar

Abra (ou gere) um projeto Node pequeno com um script que você possa
executar, por exemplo uma rota Express ou um simples `node server.js`.

## Passos

1. **Abra um arquivo de código.** Realce, correspondência de chaves,
   dobramento de código e marcação de ocorrências aparecem sozinhos. O
   **Navegador** mostra o esquema do arquivo; os language servers
   (instalados conforme as dicas de `Ferramentas ▸ Doutor do ambiente…`)
   acrescentam completar e diagnósticos.

2. **Ponha um ponto de interrupção.** Clique na margem do editor numa linha
   dentro do seu handler — aparece o marcador do ponto de interrupção.

3. **Depure o arquivo.** Rode **Depurar arquivo** (ou “Depurar no Chrome
   (pontos de interrupção)” para uma página HTML/JS). Um aviso único de
   Confiança no espaço de trabalho protege o lançamento; depois o adaptador
   `js-debug` embutido inicia o seu programa.

4. **Atinja o ponto de interrupção.** Dispare o caminho do código (faça a
   requisição, ou deixe o script chegar à linha). A execução **para** no
   seu ponto de interrupção — inspecione variáveis, percorra a pilha de
   chamadas, avance por cima ou entre na função. Na depuração do navegador,
   um Chrome com perfil descartável abre na URL do seu servidor de
   desenvolvimento, e os pontos de interrupção da página voltam para a IDE.

## O que você aprendeu

- O editor trata mais de 70 linguagens como cidadãs de primeira classe
  (gramáticas TextMate + CSL + LSP); arquivos de configuração (YAML, TOML,
  Dockerfile, nginx…) também entram.
- A depuração de JS/TS vem embutida — um multiplexador de sessões achata as
  sessões filhas do js-debug para que o depurador de sessão única da
  plataforma consiga comandá-lo.
- Todo lançamento de depuração passa pela confiança e, ao parar, é
  encerrado como uma árvore de processos inteira (sem órfãos).

## Próximos passos

- **Executar o teste em foco** depura um único método de teste, em cada
  linguagem.
- Os diagnósticos das ferramentas do rack (eslint/tsc/phpstan) chegam à
  janela **Itens de ação** da plataforma.
