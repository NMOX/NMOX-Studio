# NMOX Studio — Guia do usuário

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Tradução parcial: os capítulos 1–3 estão em português. Para o resto, veja o [guia completo em inglês](user-guide.md).

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
