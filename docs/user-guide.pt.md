# NMOX Studio — Guia do usuário

> Tradução parcial: os capítulos 1–2 estão em português. Para o resto, veja o [guia completo em inglês](user-guide.md).

Como usar o produto. Este guia percorre os recursos na ordem em que você vai encontrá-los: instalação, primeira execução, projetos, o rack, os estúdios, os assistentes e as redes de segurança.

---

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
