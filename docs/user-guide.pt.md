# NMOX Studio — Guia do usuário

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

Como usar o produto. Este guia percorre os recursos na ordem em que você vai encontrá-los: instalação, primeira execução, projetos, o rack, os estúdios, os assistentes e as redes de segurança.

---

<a id="1-install"></a>
## 1. Instalação

**macOS (recomendado):**
```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

A linha `brew trust` é a confirmação única do Homebrew para qualquer tap de terceiros: nas atualizações ninguém pergunta de novo. O aplicativo é assinado ad-hoc mas não notarizado, então uma cópia em quarentena seria recusada pelo Gatekeeper na primeira execução: o cask remove o atributo de quarentena sozinho em uma etapa `postflight` e diz isso na saída da instalação. Nada silencioso.

**Todo o resto:** baixe um arquivo da [última versão](https://github.com/NMOX/NMOX-Studio/releases/latest) — `.dmg` para macOS, `-setup.exe` para Windows, `.deb` para Debian/Ubuntu, `.tar.gz` genérico para Linux. Os quatro trazem o próprio ambiente de execução do Java; não é preciso instalar nada antes. O `-portable.zip` é o único artefato que usa o seu Java (precisa de Java 21+ no PATH, ou inicie com `--jdkhome <caminho-do-jdk>`).

> **macOS, primeira execução:** o aplicativo é assinado ad-hoc mas não notarizado, então o Gatekeeper pergunta antes de executá-lo. Na primeira vez, **clique com o botão direito no app → Abrir** e confirme, ou execute
> `xattr -d com.apple.quarantine "/Applications/NMOX Studio.app"`. Qualquer um dos dois resolve para sempre.

### Atualizar

A IDE se atualiza sozinha: **Ferramentas ▸ Plugins ▸ Atualizações** oferece os módulos de qualquer versão mais nova. Instale, reinicie quando for pedido e pronto — sem baixar o aplicativo inteiro de novo. Uma ressalva honesta: o ambiente Java embutido e o iniciador só mudam com um instalador completo, então para saltos grandes de plataforma continua certo instalar de novo a partir de um arquivo da versão.

<a id="2-first-launch"></a>
## 2. Primeira execução

No terminal, `nmoxstudio --open <pasta>` inicia o aplicativo com essa pasta aberta como projeto e o rack apontado para ela — a mesma porta que “Abrir pasta…” abre na página de boas-vindas.

A IDE abre com todas as abas do conjunto ao lado da área do editor: **Bem-vindo → Rack de tarefas → Estúdio de banco de dados → Estúdio de contratos → Designer de infraestrutura → Estúdio de API → Painel do Docker** — cada superfície principal a um clique desde o primeiro minuto. No painel esquerdo: **Estúdio de projeto** (árvore de arquivos e modelos), a base **Bancada** e o **Explorador NPM**. Uma pasta `~/NMOX` é criada como espaço de trabalho padrão; o rack aponta para lá até você abrir um projeto.

![Primeira execução — a página de boas-vindas com todas as abas abertas](images/welcome.png)

Atalhos que valem o primeiro dia (todos também aparecem na aba de boas-vindas):

| Atalho | Abre |
|---|---|
| **⌘I** | Busca rápida — alcança tudo |
| **⌘9** | Rack de tarefas |
| **⌥⌘0** | Bancada |
| **⌥⌘3** | Cliente de bate-papo IRC |
| **⌥⌘4** | Navegador (WebKit integrado, com DevTools) |
| **⌥⌘5** | Estúdio de blocos |
| **⌥⌘6** | Estúdio de contratos |
| **⌥⌘7** | Estúdio de banco de dados |
| **⌥⌘8** | Estúdio de API |
| **⌥⌘9** | Designer de infraestrutura |
| **⌘8** | Painel do Docker |
| **⌘7** | Estrutura do arquivo atual |
| **⇧⌘N / ⌥⌘O** | Novo projeto… / Abrir pasta… |
| **⇧⌘E / ⇧⌘L** | Novo experimento… / Novo espaço de aprendizado… |

<a id="3-projects"></a>
## 3. Projetos

**Abrir:** qualquer pasta com um dos 60 manifestos reconhecidos abre como um projeto de verdade — `package.json`, `Cargo.toml`, `go.mod`, `pom.xml`, `composer.json`, `foundry.toml`, `bower.json`, `Gruntfile.js` e afins — incluindo os manifestos das redes de contratos: um repositório Aiken (`aiken.toml`) ou Clarinet (`Clarinet.toml`) abre com suas trilhas reais já ligadas. Uma pasta simples de HTML com tags `<script>` e **sem** manifesto também abre, como projeto STATIC: a web clássica é de primeira classe, não um erro.

**Criar:** *Novo projeto…* oferece andaimes de verdade — Angular, Vue, Svelte, JavaScript puro, Elixir/Phoenix, PHP Web (LEMP) e Web clássica (jQuery). Cada um chega com as configurações de lint, formatação e testes já ligadas e um repositório git iniciado: um único commit de andaime que, quando o assistente roda a instalação para você, também carrega o arquivo de bloqueio — de modo que seu primeiro `git status` vem limpo.

**Trocar de projeto é seguro:** se há dispositivos rodando (um servidor de desenvolvimento, um observador), a IDE pergunta antes de trocar e os desliga com limpeza. Nada continua rodando pelas suas costas, nunca. Nem forçar o encerramento da IDE deixa um processo órfão.

**Experimentos** são o jeito mais rápido de experimentar uma pilha. **Arquivo ▸ Novo experimento…** (⇧⌘E) escolhe um modelo e gera um projeto descartável em `~/.nmox/experiments`: sem git, sem recentes, já confiado, dependências instaladas — para que a **primeira execução simplesmente funcione**. Ele abre no próprio roteiro `EXPERIMENT.md`, que diz o que apertar, qual arquivo mudar e onde mora a inteligência da IDE para aquela pilha. Guarde o que virar alguma coisa: **Arquivo ▸ Experimentos…** ▸ **Promover** o tira de lá e inicia o git, **Duplicar** cria uma cópia ao lado para uma segunda abordagem, **Descartar** limpa o resto. A prateleira mostra a idade de cada um e seu custo medido em disco. Prefere o caminho guiado? A caixa de diálogo põe à frente os 93 espaços de aprendizado.

![A prateleira de espaços de aprendizado — quantidade, custo em disco, idade e todo o ciclo de vida](images/spaces-shelf.png)

![Um experimento Express recém-criado: o roteiro aberto, as dependências instaladas, a API já servindo](images/experiment-walkthrough.png)

**Executar, construir, testar — e parar:** o ▶ da barra (F6) executa o projeto do jeito que a cadeia de ferramentas dele executa: um script `start` se o package.json tiver um, `cargo run`, `go run`, `dotnet run`, e para uma pasta de HTML um pequeno servidor estático na primeira porta livre a partir de 8080. Construir, Testar e Limpar ficam ao lado e no menu Executar. Um servidor de desenvolvimento que anuncia seu endereço acende o indicador ⇄ na barra de status e abre a página no navegador embutido. Tudo passa, na primeira vez, pela confirmação de confiança do espaço de trabalho. Uma execução que não conseguiu começar diz isso e oferece abrir o Doutor do ambiente. Para parar: o ■ à direita de Depurar (⌥⌘.) para todos os comandos em execução de uma vez e diz o que parou; **Executar ▸ Parar** para um e depois oferece **Repetir**. O ■ enxerga tudo que o produto inicia para você, instalações incluídas; ao passar o cursor, a dica nomeia exatamente o que uma pressão pararia, e desde quando cada coisa está rodando.

**`.env` em todo lugar:** se seu projeto tem um `.env`, os dispositivos lançados a partir do rack recebem essas variáveis. Edite-o e a barra de status anota que reinícios vão pegá-lo — processos em execução mantêm honestamente o ambiente antigo.

<a id="4-the-task-rack"></a>
## 4. O rack de tarefas

![O rack de tarefas](images/tabs/the-task-rack.png)

O rack é o coração do produto. Cada ferramenta do seu fluxo de trabalho — npm, o empacotador, o executor de testes, o servidor de desenvolvimento, o linter, o git, a implantação — é um dispositivo físico num rack: os botões giratórios escolhem a tarefa, o GO executa, os LEDs mostram o estado e um visor conta em palavras o que aconteceu.

![O rack apontado para um site jQuery clássico — a predefinição Classic Web Bench: MAESTRO, CRATE, DYNAMO (seu botão TASK leu o Gruntfile de verdade), IGNITION servindo estático, VITALS vigiando a qualidade](images/task-rack.png)

**O básico:**

- **Adicione dispositivos** arrastando-os da paleta (ela tem categorias e um filtro de busca). Cada dispositivo traz seu cartão *Como usar*.
- **Rode alguma coisa** apertando o botão GO de um dispositivo. Passe o cursor antes: a dica mostra a linha de comando exata que será executada. Sem mágica.
- **Ligue um pipeline:** aperte **Tab** para girar o rack e ver a traseira. Puxe um cabo de patch da entrada **OK** de um dispositivo até a entrada **GO** do seguinte. Agora `instalar → construir → testar` é uma tecla só: a corrente anda sozinha e para na primeira falha. A saída rola pela tela de fósforo do dispositivo MONITOR.
- **Desfaça qualquer mudança de estrutura** com **⌘Z** — acrescentar, remover, recabear. Remover um dispositivo em execução para antes o processo dele.
- **As predefinições** entregam um rack inteiro já cabeado em um clique — Ship Gate, Dev Intelligence, Monorepo Lanes, E2E Loop, LAMP Bench, Web3 Bench, Uptime Watch. As montagens ficam salvas por projeto automaticamente.

![Tab gira o rack — os cabos levam o MAESTRO por CRATE, DYNAMO e IGNITION até o VITALS](images/rack-rear.png)

**Coordenação, quando o pipeline cresce:**

- **QUORUM** junta trilhas: só dispara quando *todas* as suas entradas cabeadas tiverem êxito — o clássico “espere o lint E os testes E a checagem de tipos”.
- **As comportas ENABLE** nos processos longos: a entrada ENABLE de um servidor de desenvolvimento quer dizer “não comece antes disto disparar”.
- **REFLEX** observa arquivos e roteia por padrão — `src/**/*.css` para uma corrente, `**/*.ts` para outra, por trilha num monorepo.
- **ROSETTA** escolhe a trilha de ferramentas em repositórios mistos (o rack detecta Node/Rust/Go/PHP/… por diretório e aponta cada dispositivo conforme).

**Trilhas que falam a sua própria cadeia de ferramentas.** Em AUTO, os dispositivos de lint e formatação (PURITY, GLOSS) falam a cadeia do próprio projeto em vez de recorrer a ferramentas Node em toda parte: um espaço Deno usa `deno lint` e `deno fmt`, um projeto Cargo usa `cargo clippy` e `cargo fmt`, um módulo Go usa `go vet` (ou `golangci-lint`, quando o projeto traz sua configuração) e `gofmt`. Um `biome.json` vira as trilhas Node para o Biome, e as posições explícitas do botão sempre vencem o AUTO.

**Seus próprios dispositivos.** A prateleira se estende com um editor de texto: qualquer `*.json` em `~/.nmox/devices.d/` vira um dispositivo de verdade — botões, LEDs, portas e cabos, salvo na montagem e alcançável pelo ⌘I. Declare um comando como uma lista de argumentos, dê nome a um botão, e `{{botão}}` é substituído quando o botão é apertado. As leis ficam com o anfitrião, não com o seu arquivo: **a confiança do espaço de trabalho guarda a primeira execução exatamente como num dispositivo embutido**.

**As comportas de qualidade** transformam “parece pronto” em “está pronto”:

- **VITALS** roda o Lighthouse contra o seu servidor vivo e exige um piso de desempenho, acessibilidade, boas práticas ou SEO.
- **VERITAS** impõe um piso de cobertura e roda de novo exatamente os testes que falharam, pelo nome.
- **GAUNTLET** põe carga num endpoint e exige uma vazão mínima. **PRISM** vigia o tamanho do pacote, **BEACON** o certificado e a disponibilidade de uma URL, e **PREFLIGHT** é a lista de conferência antes do envio — ligue o OK dele ao seu dispositivo de implantação e as implantações fisicamente não saem enquanto tudo não estiver verde.
- **GOVERNOR** vigia regressões de gás no trabalho com Solidity (`.gas-snapshot`).

**Qualquer outra coisa:** **SOLDER** embrulha qualquer comando de shell como um dispositivo de primeira classe — e o rack inteiro **exporta para o GitHub Actions** (o seu pipeline local e a sua integração contínua são a mesma fiação). **HELM** roda comandos num servidor remoto por ssh, **TAIL** acompanha qualquer arquivo de log e **PHOSPHOR** é um terminal dentro do rack. Se o comando imprimir um endereço local, o indicador ⇄ acende como em qualquer dispositivo que sirva, e apaga quando a execução termina.

**O rack se mantém em dia sozinho.** Edite o `package.json` e o botão de scripts do NPM-9000 se atualiza no lugar. Edite um `Gruntfile` e o DYNAMO relê suas tarefas. Acrescente uma dependência e o visor do CRATE se atualiza. Sem reapontar, sem botões de atualizar.

### KVASIR — explica a última falha

![KVASIR explicando uma execução que falhou de verdade: o diagnóstico consentido no painel e os passos completos de correção no visualizador](images/kvasir-explain.png)

**KVASIR** é assistência de IA do jeito do rack: um dispositivo que explica o erro que está agora no barramento MONITOR, não uma barra lateral de conversa. Quando uma execução falha, aperte **EXPLAIN** e o KVASIR pergunta à sua IA o que deu errado e qual é o próximo passo concreto. Um veredito curto aparece no visor; **VIEW** abre a resposta inteira. **MODEL** escolhe entre **FAST** (rápido e barato, o padrão) e **DEEP** (mais forte). EXPLAIN é azul: ele lê e pergunta, nunca toca no seu projeto.

**Escolha sua IA, ponha sua chave.** O KVASIR funciona com **Claude (Anthropic)**, **ChatGPT (OpenAI)** ou **Gemini (Google)** — sua chave, sua escolha. Aperte **KEY…** para escolher o provedor e colar a chave dele; a escolha fica lembrada e a chave mora só no chaveiro do sistema operacional. As variáveis de ambiente usuais de cada provedor também são lidas, e uma chave guardada vence uma do ambiente.

**O que o KVASIR envia — e é tudo o que ele envia.** Na primeira vez que você aperta EXPLAIN, uma caixa de diálogo lista exatamente o que vai sair da sua máquina e o que não vai; nada é enviado sem esse consentimento, e o consentimento vale por provedor. Depois de um EXPLAIN bem-sucedido, o botão **VIEW** abre a resposta como uma conversa — dá para continuar perguntando sobre a mesma falha.

**Pergunte ao KVASIR sobre o seu código.** O mesmo assistente alcança o editor: selecione um trecho e escolha **Perguntar ao KVASIR sobre a seleção…**, ou **Editar com o KVASIR…** para dizer o que mudar e ver um antes e um depois antes de aplicar qualquer coisa. **⌥⌘G** completa no cursor com texto fantasma que só entra se você apertar Tab, e o indicador de ramo do git pode redigir a sua mensagem de commit.

**Aponte um agente para a sua IDE.** Ferramentas ▸ Agent Port (MCP)… abre um endpoint MCP que um assistente de fora pode consultar: ele é **somente leitura por construção**, fica desligado até você ligar, escuta apenas na interface local e exige o token gerado na partida.

O rack é extensível: plugins de terceiros podem acrescentar dispositivos (instale o NBM deles por Ferramentas ▸ Plugins). Para escrever um, veja [device-spi.md](device-spi.md).

<a id="5-the-editor"></a>
## 5. O editor

![Código jQuery na paleta NMOX Phosphor, a estrutura no Navegador](images/editor.png)

Mais de 70 linguagens são realçadas como devem — a pilha moderna, a clássica (CoffeeScript incluído) e toda a camada de configuração, até `.env`, `.editorconfig`, configurações do nginx e do Apache, Dockerfiles e arquivos de bloqueio.

- **O autocompletar** conhece o contexto e também *as bibliotecas clássicas*: se o seu projeto carrega jQuery, MooTools, Prototype, Backbone/Underscore ou Knockout (por dependências do npm *ou* por simples tags `<script>`), as APIs delas aparecem ao completar. Projetos em jQuery 1.x ou 2.x ganham uma etiqueta honesta de fim de vida, não uma cobrança.
- **O esquema do Navegador (⌘7)** mostra a estrutura do arquivo para 58 tipos; clique para saltar.
- **O minimapa** — uma silhueta do arquivo inteiro ao lado da barra de rolagem de cada editor; clique ou arraste para rolar. O documento sempre cabe inteiro na faixa: as linhas encolhem à medida que o arquivo cresce. Ver ▸ Minimapa liga e desliga em todos os editores abertos de uma vez.
- **A rolagem fixa** — as declarações que envolvem o topo da vista (a classe e depois o método em que você desceu) ficam presas acima do texto, até três linhas do próprio código; clique numa para saltar. A barra some quando nada envolve a primeira linha visível.
- **Ir para símbolo (⌥⇧⌘O)** salta para qualquer função, classe, regra ou título de todo o projeto digitando o nome, com correspondência por prefixo, por maiúsculas internas ou por curinga. O índice é limitado e honesto: `node_modules` é pulado e, num projeto muito grande, a caixa diz que indexou os primeiros 2.000 arquivos em vez de fingir que leu tudo.
- **A janela de testes (⌥⌘2)** mostra todos os testes do projeto *antes de qualquer coisa rodar*, e roda um teste, um arquivo ou todos.

### Expandir abreviação (⌥⌘E)

Digite uma abreviação do Emmet e aperte **⌥⌘E**: `ul>li*3` vira a lista pronta. Funciona em HTML, nos modelos do Angular e — na forma CSS — dentro de blocos `<style>` e atributos `style`, onde o recorte fica preso à região para que nunca engula a marcação em volta. Uma abreviação que o produto não reconhece é recusada e deixa o seu texto intacto.

### Tokens de design (propriedades personalizadas)

Digitar `var(` sugere os tokens declarados nas folhas de estilo reais do seu projeto, cada um com sua amostra de cor e o lugar onde está declarado. **⌘-clique** num uso de `var(--token)` salta para a declaração. As cores são pintadas como a cor que são — hex, `rgb()`, `hsl()`, nomes, e também `oklch()`, `lab()` e `color-mix()` — e **⌘-clique** num literal de cor abre um seletor que o substitui na mesma forma em que você escreveu.

### O atributo class conhece suas folhas de estilo

Digitar dentro de `class="…"` sugere as classes que o seu projeto realmente define, dizendo de qual folha vieram; **⌘-clique** numa classe salta para a regra dela, e **⌘-clique** num seletor `.classe` salta para o primeiro uso na marcação. **Renomear classe…** renomeia no projeto inteiro — só tokens completos, com a contagem por arquivo — e recusa em voz alta se o nome novo já existe ou se há mudanças não salvas.

### Rodar script, a partir do cursor

Na seção `scripts` de um `package.json`, **Rodar script** executa a linha onde está o cursor — pela mesma confirmação de confiança do espaço de trabalho e pelo mesmo ■ de qualquer outra execução.

### Chaves de ambiente, de primeira classe

Digitar `process.env.` ou `import.meta.env.` sugere as chaves que a sua família de arquivos `.env` realmente define, e **⌘-clique** salta para a linha que declara a chave. Os valores aparecem truncados: o lembrete está lá, o segredo não.

### Modelos do Angular, de primeira classe

Arquivos `.component.html` abrem com realce próprio de modelo, com os blocos `@if`/`@for` e as diretivas estruturais no autocompletar. Instale o Angular Language Service e a checagem de tipos do modelo chega de verdade: erre o nome de uma propriedade e o próprio compilador do Angular sugere o certo. **⌘B** num modelo salta para a definição, e o menu de contexto alterna entre o componente, seu modelo, seus estilos e seu teste.

### Componentes Vue e Svelte, de primeira classe

Arquivos `.vue` e `.svelte` abrem com realce próprio, autocompletar próprio (as runas pontuadas do Svelte 5 incluídas) e Emmet dentro dos blocos de modelo. Os diagnósticos do Vue chegam de verdade ao editor, pelo servidor de linguagem do próprio Vue.

### Depuração com pontos de parada de verdade

Clique na margem esquerda, escolha **Depurar arquivo (pontos de parada)** e o programa para ali — com a pilha, as variáveis e a avaliação de expressões. JavaScript e TypeScript funcionam de fábrica pelo adaptador embutido; Python usa debugpy e Go usa delve, que você instala. **Depurar no Chrome** faz o mesmo com uma página: os pontos de parada do seu código param dentro da IDE enquanto o navegador roda num perfil descartável. Tudo passa antes pela confirmação de confiança do espaço de trabalho.

### Apresentar e compartilhar

**Ver ▸ Modo apresentação** aumenta de uma vez todos os editores, a página do navegador embutido, a janela de saída e o terminal — e devolve tudo exatamente como estava ao sair. **Ver ▸ Mostrar teclas** mostra em tamanho grande o atalho que você acabou de apertar, mas nunca o que você digita. **Editar ▸ Copiar como Markdown** copia a seleção como bloco cercado com a etiqueta de linguagem certa, e a variante **com link** acrescenta o link do GitHub para as mesmas linhas. **Ferramentas ▸ Salvar captura…** pinta a janela inteira no dobro do tamanho, com variantes para a aba do editor sozinha, para a área de transferência e para copiar a árvore do projeto como Markdown.

<a id="6-the-studios"></a>
## 6. Os estúdios

### Acesso pelo teclado e por leitor de tela

Todo controle do rack tem um nome acessível, e isso é conferido a cada compilação. Os botões giratórios são controles deslizantes que respondem às setas, ao Início e ao Fim; os botões respondem a Espaço e Enter, inclusive os apagados, que dizem por que recusam; os LEDs e visores anunciam seu estado. Tab gira o rack — exceto quando o foco está num controle, onde ele cede lugar à navegação normal.

### Git, na barra de status

O indicador **⎇ ramo** mostra em que ramo você está e quantos arquivos mudaram; ele é lido do disco, então não custa processo nenhum. Um clique abre o histórico completo, e o menu traz **Diferenças do projeto**, **Anotar**, os pull requests pelo seu próprio `gh` e **Redigir mensagem de commit com o KVASIR**.

### Quadro de tarefas (⌥⌘1)

Um kanban por projeto salvo em `.nmoxtasks.json` — ao lado do seu código e versionado junto com ele. Arraste os cartões ou mova-os pelo teclado: **⌘↑/⌘↓** reordena, e o cartão movido mantém o foco. Os limites de trabalho em andamento são conselho, não barreira: o cabeçalho fica vermelho e nada te impede. O botão **Visão geral** troca as colunas por um painel — em andamento, concluído hoje e na semana, fluxo por dia, cartões envelhecendo — e o relógio de ponto (**Bater ponto**) mede o tempo real por cartão, com um único relógio correndo no quadro inteiro. O **Standup** transforma tudo isso num relatório pronto para colar.

### Estúdio de blocos (⌥⌘5)

Componha Web Components de verdade com peças tipadas que se encaixam, ao jeito do Scratch: aninhamentos ilegais são recusados, o código nasce como um elemento personalizado autossuficiente, e clicar numa peça realça as linhas dela. Um servidor de pré-visualização em memória mostra o componente de verdade, composto com os demais componentes válidos da sua biblioteca. A ida e volta é exata: gerar de novo o que acabou de ser lido devolve o mesmo arquivo, byte a byte.

### Estúdio de API (⌥⌘8)

Coleções, requisições, ambientes com `{{variáveis}}` e testes, salvos em `.nmoxapi.json` — com os segredos só no chaveiro, nunca nesse arquivo. Cada resposta ganha uma nota de segurança tirada dos seus cabeçalhos. Importe de curl, `.http`, OpenAPI, Postman, Insomnia e HAR; exporte para `.http` e copie como curl ou como `fetch`, com as variáveis já resolvidas.

### Estúdio de banco de dados (⌥⌘7)

SQLite, PostgreSQL, MySQL, MariaDB, MongoDB e CouchDB, com os drivers inclusos e as senhas só no chaveiro. O console conhece o motor, cada instrução tem sua própria grade de resultados, e as linhas se editam na própria grade quando há chave primária — com prévia dos UPDATEs exatos antes de aplicar, e um motivo honesto quando algo é somente leitura. Exporta para CSV ou JSON, com as fórmulas neutralizadas.

### Estúdio de contratos (⌥⌘6)

A árvore de artefatos do Foundry e do Hardhat, um **Interagir** guiado pela ABI com retornos e reversões decodificados, um painel **Observar** que acompanha blocos e eventos, e a **Supervisão** com a tabela de gás, os veredictos de tamanho EIP-170 e a agenda de endereços. **Nunca chaves privadas**: os envios usam as contas destravadas de uma rede local, e as URLs secretas moram no chaveiro.

### Designer de infraestrutura (⌥⌘9)

Uma tela para DigitalOcean, Hetzner e Cloudflare: sincronize o que existe de verdade, atualize para ver as diferenças e destrua uma pilha com o custo dela à vista. Ligações ilegais recusam em voz alta e dizem por quê, e enquanto uma operação na nuvem está em curso a tela se tranca com uma faixa que avisa.

### IRC (⌥⌘3)

Um cliente completo dentro da IDE: TLS com verificação de nome de verdade, SASL, extensões IRCv3, completar com Tab, realces, URLs que abrem no navegador embutido, registro em disco, filtros seus e uma lista de canais que você filtra enquanto digita.

### O site que vem junto

**Ajuda ▸ Site do NMOX Studio (local)** serve o site do produto a partir do próprio rack dele, na interface local. Ele fala as mesmas treze línguas que a IDE; o seletor fica no rodapé.

### Navegador (⌥⌘4)

Um navegador de verdade dentro da IDE, com ferramentas de desenvolvedor próprias — console, DOM, rede, armazenamento e painéis para Vue, Svelte e Angular — porque o motor não traz inspetor e este aqui é nosso. Ele conhece o seu código: escolha um elemento, abra a linha que o produziu, mude o estilo ali mesmo, e a declaração vai parar na folha de estilo de origem. Salvar um arquivo recarrega a página, e há tamanhos de aparelho de verdade para testar o seu layout responsivo.

<a id="7-docker"></a>
## 7. Docker

A aba Docker é um painel de controle: estado do motor, contêineres, imagens, volumes e redes, com iniciar, parar, ver registros e limpar. O dispositivo HARBOR do rack mostra o mesmo de relance. E como já foi dito: suba um contêiner de Postgres, MySQL ou Mongo e o Estúdio de banco de dados lhe oferece uma conexão pronta.

A aba **Dockerize** gera um `Dockerfile` de qualidade de produção, um `.dockerignore` e um arquivo de composição ajustados à cadeia de ferramentas do seu projeto — Node, PHP-FPM com nginx e outros.

<a id="8-wizards-and-kits"></a>
## 8. Assistentes e kits

Todos ficam em *Novo arquivo…* e no menu de contexto do projeto, e todos são **idempotentes e nunca sobrescrevem**: rodar de novo atualiza o que pertence ao próprio kit e deixa suas edições em paz; o que não pode ser reescrito aterrissa ao lado como um arquivo `.suggested`.

### Kit de padrões

`robots.txt`, `sitemap.xml`, o manifesto web, o `security.txt` do RFC 9116 e `humans.txt`, gerados a partir das suas respostas.

### Kit de PWA

Um conjunto completo de ícones forjado a partir de uma única imagem, variantes mascaráveis incluídas; um service worker legível — casca do aplicativo ou rede primeiro, você escolhe —, uma página para quando não há rede e a fiação do `index.html` que amarra tudo.

### Kit de acessibilidade

Acessibilidade como ponto de partida, não como auditoria depois do fato: `a11y.css` (um anel de foco visível, um utilitário para texto que só leitores de tela leem, estilos de link de salto e um bloco para quem prefere menos movimento), `A11Y-NOTES.md` com o percurso pelo teclado e as perguntas que nenhuma automação responde, e a fiação idempotente do `index.html` — o idioma, o link de salto, a folha de estilo. Um viewport que impede ampliar é avisado, nunca reescrito; o que o kit não consegue consertar, ele diz, sem tocar.

### Kit de internacionalização

Traduzível desde o primeiro dia, o irmão do kit de acessibilidade: `locales/en.json` e `locales/es.json` (um catálogo por idioma, as mesmas chaves), um `i18n.js` sem dependências que aplica o catálogo à marcação `data-i18n`, mantém `<html lang>` honesto e mostra uma chave faltante como ela mesma, nunca como um vazio silencioso; mais `I18N-NOTES.md` — nada de fragmentos concatenados, `Intl` para datas e números, o percurso da direita para a esquerda e a pseudolocalização.

### Kit de contratos (Web3)

Escolha uma cadeia — Solidity com Foundry, Soroban, Solana, CosmWasm, ink!, Cairo, Move, Bitcoin com Miniscript, Clarity no Stacks, Cardano com Aiken ou TON com Tact — e um nome de contrato, e o kit monta o começo já provado ao vivo: manifesto, contrato, teste nativo e um CONTRACT-NOTES.md que nomeia os dispositivos do rack e os passos de uma vez só. As chaves nunca tocam o IDE.

### Kit clássico

Acrescente a qualquer código jQuery, MooTools, Prototype, Backbone com Underscore ou Knockout, seja versionado no repositório (versões fixadas, sha256 registrado) ou como dependências do npm; mais andaimes de webpack, grunt, gulp ou bower.

<a id="9-quick-search-status-line-and-staying-oriented"></a>
## 9. Busca rápida, barra de estado e não perder o rumo

### O selo ⇄ servindo

Na barra de estado aparece um selo **⇄ servindo** sempre que há servidores no ar: a execução do próprio IDE, os dispositivos que servem e qualquer comando que tenha impresso um endereço local. Clique e escolha um: ele abre no navegador embutido, ou no do sistema quando aquela aba não dá conta dele.

### ⌘I, o localizador universal

Uma única caixa alcança seus projetos (os recentes e os conhecidos), cada dispositivo do rack — pulando direto para os seus controles —, os **servidores no ar** (Enter abre no navegador), as requisições do Estúdio de API, as conexões e tabelas do Estúdio de banco de dados, os contratos, os nós de infraestrutura e os cartões do Quadro de tarefas, cujo resultado nomeia a coluna em que o cartão está.

### A barra de estado diz o que está vivo

Ao lado do selo dos servidores estão o projeto mirado com sua cadeia de ferramentas e o ramo do Git com quantos arquivos você mudou. Tudo isso é lido do disco ou de registros que o produto já mantém: olhar não custa processo nenhum.

### A Bancada

É o porto seguro: projeto atual, arquivos abertos e recentes, projetos recentes e um atalho para cada superfície. Enquanto algo roda, a seção **EM EXECUÇÃO** abre a página — cada comando que o produto começou por você, com o endereço se ele anunciou um e desde que hora está rodando, mais cada servidor que um dispositivo do rack está servindo. Cada linha tem botões **Abrir** e **Parar** de verdade, alcançáveis pelo teclado e por leitor de tela, de modo que uma execução pode ser parada sem derrubar as outras. Todos os títulos da Bancada são botões de verdade: Tab chega, Enter abre. ⌘I alcança as mesmas execuções: digite «parar» e Enter para exatamente aquela. O que você mesmo parou se lê *parado* onde quer que o desfecho seja relatado, nunca como falha.

### Os atalhos do Emacs (e do Eclipse, e do IntelliJ)

Ferramentas ▸ Opções ▸ Mapa de teclado troca o perfil inteiro: os movimentos e o recortar e colar do Emacs em todo editor, ou os conjuntos do Eclipse e do IDEA se é ali que mora a sua memória muscular. Todo atalho do NMOX está registrado nos cinco perfis, então trocar de perfil nunca lhe custa os atalhos dos estúdios.

<a id="10-the-safety-nets-things-you-dont-have-to-do-anything-for"></a>
## 10. As redes de segurança (aquilo pelo qual você não precisa fazer nada)

### A ressurreição da sessão

O rack fotografa o que está rodando a cada poucos segundos. Um fechamento forçado, uma queda, um `kill -9` — ao abrir de novo, um aviso lhe oferece retomar exatamente a sessão perdida, com um clique.

### A garantia contra órfãos

Sair do IDE mata todo processo que ele iniciou — servidores de desenvolvimento, interpretadores, correntes, vigias —, TERM primeiro, KILL se resistirem, descendentes incluídos.

### BLACKBOX e SONAR

Ponha o **BLACKBOX** no seu rack e você tem uma caixa-preta: cada partida e cada saída, com durações, tendências e o que mudou desde a última compilação no verde. O que você mesmo parou se lê PARADO — nem verde nem falha, e nunca aquilo que se pede ao KVASIR para explicar. O **SONAR** mostra quem ocupa suas portas, cruzado com o Docker, e com um clique expulsa quem se sentou na 3000.

### Arquivos que nunca são pisados

Os quatro arquivos de trabalho dos estúdios (`.nmoxapi.json`, `.nmoxdb.json`, `.nmoxweb3.json`, `.nmoxinfra.json`) recarregam quando você os edita fora do IDE — mas se houver mudanças por salvar, você é perguntado, nunca sobrescrito. Um arquivo corrompido é posto de lado como `.bak` e relatado, nunca trocado em silêncio.

### TypeScript sem compilar

Um projeto cuja entrada seja `index.ts`, `main.ts` ou `src/index.ts` roda a partir do IGNITION com a própria remoção de tipos do Node (`--experimental-strip-types`, a partir do Node 22.6; padrão desde o 23.6 e o 22.18 LTS). A recusa de um Node mais velho é traduzida na frase que nomeia esse piso.

### O seu idioma

O NMOX Studio fala treze idiomas: English, Español, Français, Deutsch, Русский, Українська, Polski, Português (Brasil), Bahasa Indonesia, Filipino, Tiếng Việt, 简体中文 e हिन्दी. Escolha o seu em **Opções ▸ Geral ▸ Idioma** — cada um escrito no próprio nome, para você sempre achar o seu. A escolha vai para os seus ajustes de inicialização (`etc/nmoxstudio.conf`, como um argumento `--locale`) e também vale na hora. Mudam: menus, diálogos, dicas, barras de estado, a tela de boas-vindas e as opções. Fica: o vocabulário dos painéis do rack (GO, STOP, EXPLAIN — são etiquetas de aparelho, como num sintetizador), e os diálogos mais fundos da plataforma, que ainda não têm tradução.

### A checagem diária de atualizações

Discreta, uma vez por dia: se há uma versão mais nova, uma notificação leva você ao gerenciador de módulos, na aba de atualizações, onde o centro de atualizações instala os módulos novos no lugar. Desliga-se em Opções ▸ Geral.

<a id="11-learning-spaces"></a>
## 11. Espaços de aprendizagem

### Confira o seu trabalho

Alguns espaços trazem pontos de conferência: escolha um deles e **Arquivo ▸ Conferir meu trabalho** verifica os exercícios de verdade — o que os arquivos afirmam é conferido em Java puro, inclusive as conferências de *ausência*, que são o jeito de verificar «você mudou o título»: o texto original do exemplo precisa ter sumido. O que os comandos afirmam passa pela própria cadeia de ferramentas do espaço. Cada ✗ responde com a dica do próprio espaço, e quando algo falha o relatório oferece **Explicar com o KVASIR…**: os pontos que falharam e, numa conferência de arquivo, o seu próprio arquivo, limitados e sob um consentimento que diz exatamente o que sai. A resposta se lê como a de um tutor: o que mudar, e depois confira de novo.

### Os seus próprios tutoriais

Ponha um arquivo `*.json` em `~/.nmox/learn-catalog.d/` e ele entra no seletor, com o mesmo esquema dos que já vêm; um `slug` igual substitui o da casa. Dá aula? Escreva construindo: faça do exercício um projeto normal e **Arquivo ▸ Exportar como espaço de aprendizagem…** monta esse arquivo para você — os arquivos de amostra, o seu `TUTORIAL.md`, o roteiro de execução e os seus pontos de conferência —, validado contra o analisador do próprio seletor antes de ser escrito, de modo que o que você entrega aos alunos é exatamente o que o seletor deles vai carregar.

### O catálogo

*Novo espaço de aprendizagem…* oferece 93 tutoriais que já vêm juntos — linguagens, arcabouços e bibliotecas. Cada um gera um pequeno projeto de amostra, um tutorial guiado e um rack já com um **interpretador de verdade** montado: você digita no rack e um interpretador vivo responde. O botão ENGINE escolhe entre 37 interpretadores; se faltar algum, o botão INSTALL o instala ali mesmo, mostrando o progresso na tela. Os espaços moram em `~/.nmox/learn`, longe do seu trabalho de verdade.

### Primeiros passos, na tela de boas-vindas

Uma quarta coluna lista os seis primeiros gestos — abrir um projeto, rodar algo no rack, ver um servidor subir, perguntar ao KVASIR sobre código, experimentar um espaço de aprendizagem, apontar um agente para o IDE — e marca cada um a partir de registros que o produto já guarda. Cada linha é uma porta: um clique abre aquela janela ou ação. Uma marca nunca se desfaz; a coluna some quando os seis estão prontos, ou quando você aperta **Esconder esta lista**.

### As três respostas do menu Ajuda

**Novidades…** traz as notas da versão que você roda, embutidas na própria compilação; no primeiro início depois de uma atualização elas se abrem sozinhas com o que a sua instalação ainda não viu. **Relatar um problema…** monta um relato com o seu ambiente e as últimas quarenta linhas do registro, já ocultadas — a sua pasta pessoal vira `~`, o seu usuário `<user>`, e tudo que pareça uma credencial vira `[redacted]` —; você edita, e **Abrir no GitHub** preenche uma questão que você mesmo envia, ou copia. O produto nunca envia nada por conta própria. **Atalhos de teclado…** lista cada atalho do NMOX no seu perfil ativo, lido do mapa em uso, de modo que não pode divergir do que os menus fazem.

<a id="12-when-somethings-wrong"></a>
## 12. Quando algo dá errado

### O Doutor do ambiente

No menu Ferramentas, ele sonda ao vivo 66 ferramentas externas — node, npm, docker, forge, composer, gopls… — e mostra a versão encontrada e o comando de instalação do que faltar.

### Muros com porta

Se falta um servidor de linguagem ou uma ferramenta, o IDE diz qual comando rodar, ou se oferece para rodá-lo; nunca uma falha seca. Um muro tem porta própria: o TypeScript 7 não traz tsserver, então, se o TypeScript encontrado for o 7, o editor diz isso uma vez e oferece a linha 5 — a mesma que ele instala pelo mesmo motivo. Se uma porta de rede está ocupada, o erro nomeia o processo que se sentou nela, e o SONAR o expulsa.

### Um GO que não faz nada

Olhe o visor dele: os dispositivos se explicam com palavras, e a dica do botão GO mostra o comando exato que ele rodaria, para você poder tentá-lo num terminal.

### O aplicativo abre no nada (macOS)

Sem janela e sem erro, no primeiro início depois de instalar: é a quarentena do Gatekeeper — veja a nota do capítulo 1. Clique com o botão direito e Abrir, uma única vez, e fica resolvido para sempre. Os registros ficam em `~/Library/Application Support/nmoxstudio/…/var/log/` se você precisar abrir uma questão.

<a id="appendix-the-files-nmox-studio-writes-and-what-to-commit"></a>
## Apêndice: os arquivos que o NMOX Studio escreve (e quais versionar)

Tudo o que o IDE guarda de um projeto é um arquivo JSON legível na raiz do projeto, feito para ser compartilhado com o seu time.

| Arquivo | O que tem dentro | Versionar? |
|---|---|---|
| `.nmoxapi.json` | Coleções, requisições, ambientes e testes do Estúdio de API | **Sim** — o colega recebe toda a sua bancada |
| `.nmoxdb.json` | Conexões, consultas salvas e histórico | **Sim** — as senhas *nunca* estão ali (só no chaveiro) |
| `.nmoxweb3.json` | Redes e caderno de endereços do Estúdio de contratos | **Sim** — os endereços secretos *nunca* estão ali (só no chaveiro) |
| `.nmoxinfra.json` | A tela de infraestrutura: nós, fiação, propriedades | **Sim** — os tokens *nunca* estão ali (só no chaveiro) |
| `.nmoxtasks.json` | O Quadro de tarefas: colunas, cartões, limites | **Sim** — o time divide um quadro; ignore se quiser pessoal |
| `.gas-snapshot` | As referências de gás do Foundry (o GOVERNOR vigia) | **Sim** — é assim que as regressões de gás aparecem na revisão |
| `.env` | As suas variáveis de ambiente | **Não** — é exatamente para isso que existe o `.env` |
| `*.bak` | Um arquivo de trabalho ilegível, guardado para você | Não — recupere o que precisar e apague |

Edite qualquer um dos quatro arquivos `.nmox*.json` fora do IDE, ou traga as mudanças de um colega, e o estúdio correspondente recarrega sozinho — a não ser que você tenha mudanças por salvar ali, e aí ele pergunta antes.

Fora do projeto: `~/NMOX` é a bancada padrão, os experimentos moram em `~/.nmox/experiments`, os espaços de aprendizagem em `~/.nmox/learn`, e o estado do próprio IDE — disposição das janelas, patches do rack, preferências — no diretório de usuário da plataforma.
