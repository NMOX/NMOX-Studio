# Vindo do VS Code

<!-- languages -->
[English](coming-from-vscode.md) · [Español](coming-from-vscode.es.md) · [Français](coming-from-vscode.fr.md) · [Deutsch](coming-from-vscode.de.md) · [Русский](coming-from-vscode.ru.md) · [Українська](coming-from-vscode.uk.md) · [Polski](coming-from-vscode.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](coming-from-vscode.id.md) · [Filipino](coming-from-vscode.tl.md) · [Tiếng Việt](coming-from-vscode.vi.md) · [简体中文](coming-from-vscode.zh.md) · [हिन्दी](coming-from-vscode.hi.md) · [עברית](coming-from-vscode.he.md) · [العربية](coming-from-vscode.ar.md)
<!-- /languages -->

As suas mãos já sabem onde as coisas ficam. Esta página é o mapa desses
hábitos para o NMOX Studio: primeiro os atalhos, depois onde cada ideia do
VS Code mora aqui, e por fim o que é, sinceramente, diferente.

Os quatro primeiros atalhos que um usuário do VS Code aperta fazem o que
ele espera: **⇧⌘P** abre a paleta de comandos, **⇧⌘E** a árvore de
arquivos, **⇧⌘X** os plugins e **⌃\`** o terminal. Eles estão registrados
nos cinco perfis de teclado que a plataforma traz, e uma verificação do
build resolve cada um pelo mapa de teclado montado no macOS, no Windows e
no Linux, para que nada mais dispare no lugar deles.

<a id="the-chords"></a>
## Os atalhos

As colunas do macOS usam os símbolos da barra de menus (⌃ Control,
⌥ Option, ⇧ Shift, ⌘ Command); as colunas do Windows e do Linux são o
mesmo atalho num teclado de PC.

| Você quer | VS Code, macOS | NMOX, macOS | VS Code, Win/Linux | NMOX, Win/Linux |
|---|---|---|---|---|
| Paleta de comandos | ⇧⌘P | **⇧⌘P** (ou ⌘I) — Pesquisa rápida | Ctrl+Shift+P | **Ctrl+Shift+P** (ou Ctrl+I) |
| Abrir um arquivo pelo nome | ⌘P | **⌘P** — Ir para o arquivo | Ctrl+P | **Ctrl+P** |
| A árvore de arquivos | ⇧⌘E | **⇧⌘E** — Estúdio de projeto | Ctrl+Shift+E | **Ctrl+Shift+E** |
| Extensões | ⇧⌘X | **⇧⌘X** — Ferramentas ▸ Plugins | Ctrl+Shift+X | **Ctrl+Shift+X** |
| O terminal, na pasta do projeto | ⌃\` | **⌃\`** | Ctrl+\` | **Ctrl+\`** |
| Abrir um projeto recente | ⌃R | **⌥⌘P** — Alternar projeto… | Ctrl+R | **Ctrl+Alt+P** |
| Ir para um símbolo do projeto | ⌘T | **⌥⇧⌘O** | Ctrl+T | **Ctrl+Alt+Shift+O** |
| Ir para a definição | F12 | **⌘B** | F12 | **Ctrl+B** |
| Renomear um símbolo | F2 | **⌃R** | F2 | **Ctrl+R** |
| Ir para a linha | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| Comentar ou descomentar a linha | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| Mostrar sugestões | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| Acrescentar a próxima ocorrência à seleção | ⌘D | **⌘D** ou ⌘J | Ctrl+D | **Ctrl+D** ou Ctrl+J |
| Selecionar todas as ocorrências | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| Acrescentar um cursor acima / abaixo | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| Mover a linha para cima / para baixo | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| Copiar a linha para baixo | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| Apagar a linha | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| Formatar o documento | ⇧⌥F | **⌃⇧F** | Shift+Alt+F | **Alt+Shift+F** |
| Fechar a aba do editor | ⌘W | **⌘W** | Ctrl+W | **Ctrl+W** |
| O painel de problemas | ⇧⌘M | **⌘6** — Itens de ação (aqui ⇧⌘M liga e desliga um marcador) | Ctrl+Shift+M | **Ctrl+6** |
| Ligar ou desligar um ponto de interrupção | F9 | **⌘F8** | F9 | **Ctrl+F8** |
| Começar a depurar | F5 | **⇧⌘F5** — Depurar o arquivo | F5 | **Ctrl+Shift+F5** |
| Executar sem depurar | ⌃F5 | **F6** — Executar Projeto | Ctrl+F5 | **F6** |
| Configurações | ⌘, | **⌘,** — NMOX Studio ▸ Settings… | Ctrl+, | Ferramentas ▸ Opções (sem atalho) |

Todos os atalhos do NMOX nesta tabela foram lidos do mapa de teclado que é
entregue (o ⌘, é do próprio menu do aplicativo no macOS), e não de memória.
Algumas coisas não cabem numa célula:

- **F5 fica ocupado durante a depuração.** Aqui ele quer dizer *Continuar*,
  como em todas as IDEs da família NetBeans, então uma depuração começa com
  **⇧⌘F5** (Ctrl+Shift+F5) e continua com F5.
- **⌃R é Renomear aqui**, e é por isso que *Alternar projeto* fica em ⌥⌘P
  em vez do atalho de Abrir Recente do VS Code. Renomear funciona onde a
  linguagem por trás do arquivo dá suporte.
- **Ctrl+, no Windows e no Linux** volta pelo seu histórico de edição,
  como sempre foi no NetBeans; as configurações ficam em
  Ferramentas ▸ Opções (no macOS, **Settings…** no menu do aplicativo, ⌘,).

**Ajuda ▸ Atalhos de teclado…** lista todos os atalhos do NMOX no seu
mapa de teclado ativo, incluindo os quatro atalhos do VS Code, lidos do
mapa em uso, de modo que não podem divergir do que as teclas fazem.


<a id="from-the-terminal"></a>
## Pelo terminal

`code .` vira `nmox .`:

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox src/app.ts:42  # open it at line 42 (code -g's form; -g itself is accepted)
nmox            # just start the IDE
```

Ele volta na hora, e um segundo `nmox` entrega a pasta dele à IDE que já
está rodando. Uma coluna (`src/app.ts:42:7`) é aceita e o editor abre no
começo da linha; um nome que não existe é recusado no terminal em vez de
iniciar qualquer coisa. `-r` é aceito, `-n` abre na única janela, e as
opções `-a` e `-v` são recusadas pelo nome.

`-w` (`--wait`) abre um arquivo e espera até você fechar a aba dele, e `-d`
(`--diff`) compara dois arquivos lado a lado, então o NMOX Studio pode ser o
editor e o difftool do git, do jeito que `code --wait` é:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
```

Aí o `git commit` abre a mensagem na IDE; salve, feche a aba e o git segue em
frente. Sair da IDE com um arquivo ainda aberto também o devolve, com o que
tiver sido salvo.
**Equipe ▸ Usar o NMOX Studio com o Git…** define essas mesmas três linhas para você,
depois de mostrar o valor atual de cada uma.
O Homebrew, o instalador do Windows (*Adicionar
“nmox” ao PATH*) e os pacotes do Linux o põem no seu PATH; para uma
instalação pelo DMG, o [guia do usuário](user-guide.pt.md#2-first-launch) mostra o link de
uma linha.

<a id="where-each-vs-code-idea-lives"></a>
## Onde cada ideia do VS Code mora

| No VS Code | No NMOX Studio |
|---|---|
| **Explorer** (Explorador) | **Estúdio de projeto** (⇧⌘E) — a árvore de arquivos (clique com o botão direito num arquivo para Copiar caminho, Copiar caminho relativo e Mostrar no Finder), os modelos e o editor do `package.json` do projeto. A **Bancada** (⌥⌘0) é a base: arquivos abertos, arquivos recentes, projetos recentes e tudo que está rodando. |
| **Command Palette** (Paleta de Comandos) | **Pesquisa rápida** (⇧⌘P ou ⌘I) — ações, arquivos, projetos recentes, dispositivos do rack, servidores ativos, requisições do Estúdio de API, símbolos. Os nomes dos comandos do próprio VS Code também funcionam: *Format Document*, *Toggle Terminal*, *Git: Commit* ou *Open Settings* listam a ação que faz a mesma coisa aqui, em **Comandos do VS Code**, com o próprio nome e o próprio atalho. |
| **Extensions** (Extensões) | **Ferramentas ▸ Plugins** instala e atualiza módulos, incluindo as atualizações do próprio NMOX. Muito do que uma extensão acrescenta no VS Code é um **dispositivo do rack** aqui — e você pode escrever um como um arquivo JSON em `~/.nmox/devices.d` ([arquivos de dispositivo](device-files.md)). |
| **`tasks.json`** | O `.vscode/tasks.json` do seu repositório é lido: digite o nome de uma tarefa na Pesquisa rápida (⇧⌘P ou ⌘I) e Enter em *Executar tarefa: build — make all* a executa, com a Confiança no espaço de trabalho perguntando antes num projeto em que você ainda não confiou, a saída dela na janela Output e o ■ da barra de ferramentas para pará-la. Ao lado, os scripts do próprio projeto rodam como estão escritos: o Executar / Construir / Testar da barra de ferramentas (F6, F11, ⌃F6), **Executar script** numa linha de scripts do `package.json`, o **Explorador NPM** e o **Rack de tarefas** (⌘9), onde as tarefas são dispositivos que você liga uns aos outros. |
| **`launch.json`** | O `.vscode/launch.json` do seu repositório é lido: digite o nome de uma configuração na Pesquisa rápida (⇧⌘P ou ⌘I) e Enter em *Depurar: Launch Program — ${workspaceFolder}/server.js* inicia o depurador de pontos de interrupção nesse programa, com a Confiança no espaço de trabalho perguntando antes. As configurações de Node (`node`, `pwa-node`) e de Python (`python`, `debugpy`) depuram o `program` delas no `cwd` delas, com os `args` e o `env` delas; as de Chrome (`chrome`, `pwa-chrome`) abrem a `url` (ou o `file`) delas com o `webRoot` delas. Sem um `launch.json`, **Depurar o arquivo** (⇧⌘F5) e o botão de depurar da barra de ferramentas descobrem o que iniciar a partir do próprio projeto — a entrada do script `start`, o `main`, o `index.js` — e o dispositivo **INSPECTOR** do rack inicia um depurador como uma etapa de um pipeline. |
| **Integrated terminal** (Terminal integrado) | A janela **Terminal** (⌃\`): a primeira vez que você aperta, ela inicia um shell na pasta do projeto; as seguintes a trazem de volta. |
| **`settings.json`** | Ferramentas ▸ Opções (no macOS, NMOX Studio ▸ Settings…). O `.vscode/settings.json` de um repositório também é lido: `editor.tabSize`, `editor.insertSpaces` e `editor.indentSize` definem a indentação dele enquanto você digita, `files.trimTrailingWhitespace` e `files.insertFinalNewline` (quando `true`) se aplicam quando você salva, e um bloco de linguagem como `"[typescript]"` os substitui para aquela linguagem. Onde o repositório também tem um `.editorconfig`, o `.editorconfig` vence onde quer que os dois digam algo. |
| **Problems panel** (Problemas) | **Itens de ação** (⌘6), ou clique na contagem **✕ ⚠** da barra de status: os erros e avisos dos servidores de linguagem e os achados de lint e de tipos dos dispositivos PURITY e TYPEGUARD do rack. Como no VS Code, alguns servidores informam só sobre os arquivos que você tem abertos; o gopls informa sobre o pacote inteiro. |
| **Outline** (Estrutura de tópicos) | O **Navegador** (⌘7). |
| **Source Control** (Controle do código-fonte) | O selo do git na barra de status (ramo e mudanças, um clique até o histórico) e o menu **Equipe**. |
| **Workspace Trust** (Confiança do workspace) | A mesma ideia, aplicada antes de qualquer coisa que um repositório escolheu ser executada: abrir um projeto clonado não roda nada até você confiar nele. |
| **Keyboard Shortcuts editor** (Editor de atalhos de teclado) | Ferramentas ▸ Opções ▸ Atalhos de teclado (no macOS, Settings… ▸ Atalhos de teclado) — edite qualquer atalho, ou troque o perfil inteiro para Eclipse, Emacs ou IntelliJ. |

Na primeira vez que você abre um repositório que traz `.vscode/tasks.json`,
`launch.json` ou `settings.json`, um aviso diz o que foi encontrado e onde
fica; clique nele para abrir a Pesquisa rápida. Ele diz isso uma vez por
projeto.

<a id="what-is-honestly-different"></a>
## O que é, sinceramente, diferente

- **⌘D acrescenta a próxima ocorrência no perfil de teclado padrão, não
  em todos os perfis.** O perfil do Eclipse mantém ⌘D como o *Delete
  Line* do Eclipse, e o perfil do NetBeans 5.5 como *Shift Line Left*;
  nesses, ⌘J (Ctrl+J) é o mesmo gesto.
- **⌃\` abre o Terminal e dá foco a ele; não o esconde.** E enquanto o
  Terminal tem o foco, as teclas pertencem ao seu shell, então o segundo
  toque chega ao shell em vez de levar você de volta ao editor.
- **O `launch.json` é lido, e o que o depurador não consegue respeitar é
  recusado.** O depurador aqui passa um programa, a pasta de trabalho
  dele, os `args` dele (uma lista de strings) e o `env` dele (strings
  acrescentadas ao ambiente herdado), então uma configuração que define
  `envFile`, `runtimeExecutable`, `runtimeArgs`, `preLaunchTask` ou
  qualquer outro campo que ele não aprendeu aparece na lista mas não é
  iniciada: Enter nomeia os campos na barra de status. Iniciar o programa
  sem eles depuraria outra coisa que não o que o arquivo diz. `args`
  escritos como uma só string (o VS Code a entrega a um shell) e um valor
  de `env` igual a `null` (que remove uma variável) são recusados do mesmo
  jeito, assim como `"request": "attach"`, uma entrada de
  `compounds`, um tipo sem adaptador aqui (`go`, `msedge`, `cppdbg` e os
  demais), um valor que só o VS Code pode fornecer (`${file}`,
  `${input:…}`) e um caminho fora do projeto. Os campos que só moldam o
  que o depurador mostra — `skipFiles`, `outFiles`, `sourceMaps`,
  `console`, `justMyCode`, `presentation` — são aceitos e não aplicados; a
  saída do programa vai para a janela Output.
- **O `tasks.json` é lido, e o que não pode rodar como está escrito é
  recusado.** Uma tarefa que usa um valor
  que só o VS Code pode fornecer (`${input:…}`, `${file}`, `${config:…}`,
  `${command:…}`) ou que tem um `dependsOn` para outra tarefa aparece na
  lista mas não é executada: Enter diz qual variável ou qual tarefa na
  barra de status. Executá-la com o valor em branco, ou sem a tarefa de
  que depende, executaria outra coisa que não o que o arquivo diz. O mesmo
  faria um tipo de tarefa que vem de uma extensão (`gulp`, `typescript`) e
  uma pasta de trabalho fora do projeto.
- **Uma tarefa `"type": "shell"` roda no shell que o VS Code usaria.** No
  macOS e no Linux é o seu `$SHELL` com `-c` (um zsh, bash ou fish do
  macOS inicia como shell de login, `-l`, como fazem os perfis padrão do
  VS Code); no Windows é o PowerShell, o `pwsh` quando está instalado. O
  `options.shell` é respeitado do jeito do VS Code: nomeie um `executable`
  e ele roda com exatamente os `args` que você der, então um bash precisa
  de `"args": ["-c"]`. No Windows só são executados o PowerShell (args
  terminando em `-Command`) e o `cmd.exe` (args terminando em `/c`);
  qualquer outro shell ali é recusado pelo nome em vez de receber uma
  linha de comando com aspas postas no chute.
- **Não existe um perfil de teclado “VS Code”.** Os atalhos acima vêm no
  perfil padrão e nos outros quatro. Uma exceção proposital: no perfil do
  **Eclipse**, ⇧⌘E continua sendo o *Switch to Editor* do próprio Eclipse,
  e dentro do editor ⇧⌘P e ⇧⌘X mantêm os significados do Eclipse (a chave
  correspondente, maiúsculas) — quem escolheu o Eclipse espera o Eclipse.
- **No Linux, Ctrl+\` abre o Terminal, não um seletor de janelas.** O
  seletor fica em Ctrl+Tab. Num ambiente que toma Ctrl+Tab para si (o KDE,
  por exemplo), **Janela ▸ Documentos…** lista os arquivos abertos.
- **Os atalhos com Ctrl+Alt podem colidir com o AltGr.** No Windows, os
  layouts de teclado que digitam caracteres com AltGr (o polonês, por
  exemplo) enviam Ctrl+Alt para ele. Se Ctrl+Alt+P ou Ctrl+Alt+K digita um
  caractere para você, mova *Alternar projeto* ou os atalhos de
  experimento em Atalhos de teclado.
- **As extensões do VS Code não se instalam aqui.** A inteligência de
  linguagem vem dos servidores de linguagem que o NMOX conhece (o Doutor do
  ambiente lista o que falta e como instalar), das gramáticas do próprio
  editor e dos plugins feitos para a NetBeans Platform.
