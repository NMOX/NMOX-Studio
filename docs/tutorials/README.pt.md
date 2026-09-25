# Tutoriais do NMOX Studio

<!-- languages -->
[English](README.md) · [Español](README.es.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [Русский](README.ru.md) · [Українська](README.uk.md) · [Polski](README.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](README.id.md) · [Filipino](README.tl.md) · [Tiếng Việt](README.vi.md) · [简体中文](README.zh.md) · [हिन्दी](README.hi.md) · [עברית](README.he.md) · [العربية](README.ar.md)
<!-- /languages -->

Roteiros curtos, para fazer com a mão na massa, dos sistemas que tornam o
NMOX Studio diferente de uma IDE genérica. Cada um cabe numa sentada: abra
a janela, siga os passos e você terá usado o recurso de verdade.

Para a referência completa (instalação, cada menu, cada rede de segurança)
veja o [Guia do usuário](../user-guide.pt.md). Para a lista completa de
dispositivos veja [devices.md](../devices.md).

## Os sistemas

| Tutorial | O que você vai fazer | Onde abre |
|----------|----------------------|-----------|
| [O Rack de tarefas](the-task-rack.pt.md) | Ligar uma montagem executar→monitorar e vê-la disparar | ⌘9 / aba Rack de tarefas |
| [Escreva o seu próprio dispositivo](your-own-device.pt.md) | Acrescentar um dispositivo ao rack com um editor de texto — sem Java, sem reiniciar | `~/.nmox/devices.d/` |
| [Bancada](workbench.pt.md) | Usar a base para pular entre projetos e ferramentas | ⌥⌘0 |
| [Estúdio de projeto](project-studio.pt.md) | Gerar um projeto e executá-lo sem terminal | aba Estúdio de projeto |
| [Estúdio de API](api-studio.pt.md) | Enviar uma requisição, verificá-la e ler a nota de segurança | ⌥⌘8 |
| [Estúdio de banco de dados](db-studio.pt.md) | Conectar ao SQLite e editar uma linha na grade | ⌥⌘7 |
| [Estúdio de contratos](contract-studio.pt.md) | Compilar, implantar numa cadeia local e chamar um contrato | ⌥⌘6 (Web3) |
| [Designer de infraestrutura](infra-designer.pt.md) | Desenhar um droplet + firewall e simular a implantação | ⌥⌘9 |
| [Estúdio de blocos](block-studio.pt.md) | Montar um Web Component com blocos que se encaixam | ⌥⌘5 |
| [Edição poliglota e depuração](polyglot-editing-and-debugging.pt.md) | Pôr um ponto de interrupção num app Node e fazê-lo parar ali | abra qualquer projeto |
| [Do navegador ao código-fonte](browser-to-source.pt.md) | Clicar num elemento da página, cair no código-fonte dele e mudar o estilo pelo DevTools | ⌥⌘4 → DevTools → DOM |
| [O Agent Port (MCP)](agent-port.pt.md) | Apontar um agente de IA para o estado vivo da IDE — somente leitura por construção | Ferramentas ▸ Agent Port (MCP)… |
| [A segunda semana](the-second-week.pt.md) | Fazer commit, revisar um diff, resolver um conflito, abrir um pull request e rastrear um stack trace — os passos do próprio git, na janela em que você trabalha | Equipe ▸ Usar o NMOX Studio com o Git… |
| [O Painel do Docker](docker-panel.pt.md) | Inspecionar contêineres e dockerizar um projeto | aba Painel do Docker |
| [O Quadro de tarefas e os sprints](task-board.pt.md) | Tocar um kanban com relógio de ponto, standup de um clique e burndown de sprint a partir de um único arquivo versionado | ⌥⌘1 |
| [Mostre para uma sala](show-it-to-a-room.pt.md) | Apresentar, compartilhar e capturar a tela de dentro da IDE — do Modo de apresentação a Copiar árvore do projeto como Markdown | Exibir ▸ Modo de apresentação |
| [KVASIR](kvasir.pt.md) | Perguntar à IA por que uma execução falhou | Rack → KVASIR |
| [Explique qualquer coisa](explain-anything.pt.md) | Usar as quatro faces do KVASIR: execuções, código, respostas de API, erros de banco | onde quer que algo falhe |
| [Migrando do Postman](migrating-from-postman.pt.md) | Importar suas coleções, capturas HAR e mais — os segredos vão para o chaveiro | ⌥⌘8 → Importar… |
| [Image Kit (Web)](image-kit.pt.md) | Comprimir as imagens de um projeto: JPEGs menores, irmãos WebP, relatório honesto | Arquivo ▸ Adicionar ao projeto ▸ Image Kit (Web)… |
| [Espaços de aprendizado](learning-spaces.pt.md) | Criar um ambiente guiado com um REPL vivo | Novo espaço de aprendizado… |
| [Assistentes e kits](wizards-and-kits.pt.md) | Acrescentar um PWA, arquivos de padrões ou estruturas da web clássica | Arquivo ▸ Adicionar ao projeto |

> **Uma nota sobre os atalhos.** No macOS os estúdios ficam na família
> `⌥⌘` (Option-Command) — `⌥⌘6`–`⌥⌘9`, `⌥⌘5`, `⌥⌘0` — porque os atalhos
> simples com `⇧⌘` já são da plataforma. No Linux e no Windows o
> modificador é `Alt+`; os menus (Janela ▸ …) sempre funcionam.

A primeira execução mostra três abas — Bem-vindo, Rack de tarefas e
Navegador web — com o Estúdio de projeto, a Bancada e o Explorador NPM
encaixados ao lado. Cada uma das outras janelas está a um atalho e aparece
na coluna FERRAMENTAS da página Bem-vindo.
