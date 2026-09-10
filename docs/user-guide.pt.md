# NMOX Studio — Guia do usuário

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Tradução parcial: os capítulos 1–4 estão em português. Para o resto, veja o [guia completo em inglês](user-guide.md).

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
