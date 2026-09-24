# Glossário

<!-- languages -->
[English](glossary.md) · [Español](glossary.es.md) · [Français](glossary.fr.md) · [Deutsch](glossary.de.md) · [Русский](glossary.ru.md) · [Українська](glossary.uk.md) · [Polski](glossary.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](glossary.id.md) · [Filipino](glossary.tl.md) · [Tiếng Việt](glossary.vi.md) · [简体中文](glossary.zh.md) · [हिन्दी](glossary.hi.md) · [עברית](glossary.he.md) · [العربية](glossary.ar.md)
<!-- /languages -->

As palavras que o NMOX Studio usa e que outra IDE não usa, mais os termos
do NetBeans que aparecem aqui e ali. Cada verbete diz o que a palavra
significa aqui e onde ler mais.

<a id="the-rack"></a>
## O rack

**Rack de tarefas** (⌘9) — A janela onde as suas ferramentas rodam. Cada tarefa (instalar, construir, testar, servir,
verificar, implantar) é um *dispositivo* montado num rack, como o equipamento de um estúdio de som.
[Guia do usuário §4](user-guide.pt.md#4-the-task-rack).

**Dispositivo** — Uma ferramenta no rack, por exemplo VELOCITY (Vite), VERITAS (testes) ou
PURITY (lint). Um dispositivo tem um painel frontal com botões giratórios,
botões, luzes e um pequeno visor, e um painel traseiro com *conectores*.
Há 53 dispositivos embutidos, listados na [referência de dispositivos](devices.md).
Você pode acrescentar os seus como um arquivo JSON em `~/.nmox/devices.d/`
([arquivos de dispositivo](device-files.md)).

**Painel frontal** — A frente de um dispositivo. Aperte **Tab** no rack para virá-lo e ver o
painel traseiro.

**Conector** — Uma tomada no painel traseiro de um dispositivo. Os conectores de saída enviam sinais e os de
entrada os recebem. Há três tipos de sinal:
- Um **gatilho** (trigger) é um único pulso: “o build terminou”, “OK”, “FAIL”.
- Uma **comporta** (gate) fica ligada ou desligada: “o servidor está no ar”.
- **Dados** levam texto, como uma URL ou uma linha de saída.

**Cabo** — Uma ligação de um conector de saída a um conector de entrada. Ligue o conector OK de um
build ao conector RUN do executor de testes, e os testes rodam sempre que
um build passa. Para ligar dois conectores, arraste de um até o outro, ou
clique num e depois no outro.

**Patch** — Um rack inteiro: os dispositivos, as configurações deles e os cabos. Fica salvo ao lado
do projeto como `.nmoxrack.json`, então vale a pena versioná-lo.

**Predefinição** — Um patch pronto que você carrega pelo menu **Predefinições** do rack, por exemplo
*Portão de publicação* ou *Ciclo E2E*. Salve qualquer patch em `~/.nmox/presets.d/` e
ele também aparece no menu.

**Rack inicial** — O patch que um projeto recebe na primeira vez que você o abre, escolhido pelo
tipo de projeto: um console Vite para um aplicativo Vite, trilhas de
executar, depurar e testar para um crate Cargo, e assim por diante.

**Trilha** — Dois sentidos, ambos sobre rodar coisas:
- Um **pipeline**: uma corrente de dispositivos ligados por cabos, como instalar →
  construir → testar. Várias trilhas podem rodar lado a lado, e o QUORUM espera
  todas terminarem.
- A **trilha AUTO** de um dispositivo: o comando que ele escolhe para este projeto. Em
  AUTO, o dispositivo de testes roda `npm test` num projeto Node e
  `cargo test` num projeto Rust.

**Compartilhar… / Importar…** — Salve um rack num arquivo para outra pessoa, ou carregue um que ela mandou. Antes
de qualquer coisa ser montada, Importar mostra tudo o que o arquivo contém, e
todo dispositivo chega desligado.

**Galeria de racks** — **Ferramentas ▸ Galeria de racks…** lista racks da comunidade, predefinições, racks iniciais
e os racks que você salvou. Cada item mostra para que serve e de quais ferramentas
precisa. [Racks da comunidade](racks.md).

<a id="projects-and-running"></a>
## Projetos e execução

**Apontar** / **projeto apontado** — O projeto em que a IDE está trabalhando agora. Abrir um projeto aponta
para ele: o rack, os estúdios, a barra de status e o Executar seguem o projeto
apontado. Apontar para outro troca todos eles, e o que ainda estiver
rodando é parado antes, depois de perguntar.

**Confiança no espaço de trabalho** — A pergunta que o NMOX Studio faz antes de rodar pela primeira vez o código do próprio projeto
(scripts, builds, testes). Se você responde
**Manter seguro**, nada do projeto roda; **Confiar no espaço de trabalho** permite. A sua resposta fica lembrada
por pasta.

**▶ e ■** — Executar e Parar na barra de ferramentas. ▶ (F6) executa o projeto apontado. ■ (⌥⌘.)
para todos os comandos que o NMOX Studio iniciou para você.

**Selo ⇄** / **servindo** — Quando algo que você roda imprime um endereço local, como
`http://localhost:5173/`, o endereço aparece na barra de status depois de
um símbolo ⇄. Esse servidor rodando é o que “está servindo”. Clique no endereço para abri-lo
no Navegador web. A Pesquisa rápida lista esses servidores em *Servidores ativos*.

**Experimento** — Um projeto descartável criado a partir de um modelo em `~/.nmox/experiments`. As
dependências já vêm instaladas e ele já é confiável. **Promova-o**
para guardá-lo, ou **descarte-o**. **Arquivo ▸ Novo experimento…**.

**Espaço de aprendizado** — Um tutorial guiado para uma linguagem ou um framework. Ele cria um projeto de
verdade, um roteiro e um rack montado com um REPL vivo, e
**Arquivo ▸ Verificar meu trabalho** confere os seus exercícios. São 93.
**Arquivo ▸ Novo espaço de aprendizado…**.

**PREFLIGHT** — O dispositivo de conferência antes do envio. Ele roda as verificações que o seu projeto define (lint,
tipos, testes, build) como um único passou ou falhou.

**Primeiros passos** — A lista de conferência na aba Bem-vindo (a coluna **PRIMEIROS PASSOS**). Os passos se marcam sozinhos quando você
os faz e nunca se desmarcam.

<a id="the-windows"></a>
## As janelas

**Estúdio** — Uma janela com uma ferramenta própria para um tipo de trabalho. São
cinco: o **Estúdio de API** (⌥⌘8), o **Estúdio de banco de dados** (⌥⌘7), o **Estúdio de contratos** (⌥⌘6,
contratos inteligentes), o **Estúdio de blocos** (⌥⌘5, componentes web montados com blocos)
e o **Designer de infraestrutura** (⌥⌘9, infraestrutura na nuvem). Cada um salva o seu trabalho
ao lado do projeto num arquivo `.nmox*.json`. O **Estúdio de projeto** tem o mesmo
nome, mas é a árvore de arquivos e os modelos de projeto.

**Bancada** (⌥⌘0) — A base: o que está rodando, o que está aberto, e os seus projetos e
arquivos recentes.

**Quadro de tarefas** (⌥⌘1) — Um quadro kanban para cada projeto, com sprints e um relógio de ponto, salvo como
`.nmoxtasks.json`.

**Bem-vindo** — A aba inicial: ações para começar, projetos recentes, a coluna *FERRAMENTAS*
que lista todas as janelas, e os Primeiros passos.

<a id="ai"></a>
## IA

**KVASIR** — O nome dos recursos de IA do NMOX Studio: Perguntar, Editar, Completar, Explicar,
Rascunhar mensagem de commit. Ele funciona com Claude, ChatGPT ou Gemini usando a sua
própria chave de API, guardada no chaveiro do sistema operacional. Cada recurso pede o
seu consentimento uma vez e diz exatamente o que vai enviar. Nada é enviado
até você usar um recurso. Versões anteriores o chamavam de ORACLE.

**Agent Port** — **Ferramentas ▸ Agent Port (MCP)…** dá a um agente de IA que roda na sua
máquina, como um assistente de programação, acesso somente leitura ao estado da IDE
por MCP: arquivos abertos, diagnósticos, execuções, símbolos. Ele é somente leitura por
construção e escuta apenas na sua própria máquina.
[Tutorial](tutorials/agent-port.md).

<a id="netbeans-terms-you-may-see"></a>
## Termos do NetBeans que você pode ver

O NMOX Studio é construído sobre a NetBeans Platform, e algumas das palavras dela
aparecem.

**Módulo** / **NBM** — Uma parte do aplicativo. Um *NBM* é o arquivo em que um módulo é entregue.
**Ferramentas ▸ Plugins** instala as atualizações módulo por módulo.

**Centro de atualizações** — De onde **Ferramentas ▸ Plugins ▸ Atualizações** tira as versões novas dos
módulos do NMOX Studio. Ele lê um catálogo publicado com cada versão no GitHub.

**userdir** — A pasta onde o NMOX Studio guarda as configurações, a disposição das janelas, os registros e
as atualizações instaladas. Para encontrá-la, abra **Ajuda ▸ About**. O registro fica em
`var/log/messages.log`. Para começar com configurações limpas, inicie com
`--userdir <an empty folder>`.

**Opções** / **Settings…** — A caixa de diálogo de preferências. No macOS é **NMOX Studio ▸ Settings…**,
e no Windows e no Linux é **Ferramentas ▸ Opções**.

**Itens de ação** — A janela que lista os problemas encontrados no projeto, incluindo os resultados de
lint e de checagem de tipos do rack. Clique num problema para ir até a linha dele.
