# marionete-god

[![CI](https://github.com/ronnyarruda20/marionete-god/actions/workflows/ci.yml/badge.svg)](https://github.com/ronnyarruda20/marionete-god/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring AI 2.0](https://img.shields.io/badge/Spring%20AI-2.0-6db33f)
![MCP](https://img.shields.io/badge/MCP-server-6b4fbb)
![Licença MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-green)

Servidor **MCP (Model Context Protocol)** em Java, com Spring AI, que dá a um assistente de IA controle real de um celular Android pelo `adb`: ler a tela, tocar, deslizar, digitar, abrir apps e URLs.

A IA puxa os fios. O celular se mexe. Você decide até onde os fios alcançam.

Com ele conectado, você pede ao Claude (ou a qualquer cliente MCP) coisas como:

> "Abre o site da loja no celular e me diz se o botão de comprar aparece sem rolar."
>
> "Entra nas configurações e liga o modo avião."
>
> "Espera a página carregar, tira um print e me fala o que quebrou no layout."

**Em resumo:**
- **16 ferramentas**: leitura de tela, toque, deslize, digitação, teclas, apps, URLs e shell.
- **Travas no servidor, não no prompt**: apps negados, confirmação humana pelo protocolo, uma sessão de IA por vez e um botão de desligar.
- **Dois modos**: padrão, travado para quem baixa, e god, explícito para quem sabe o que tem no aparelho.
- **Tudo registrado**: cada chamada vira uma linha em `registro.jsonl`.
- **44 testes sem aparelho**, com um cliente MCP de verdade conversando com o servidor.

## Por que mais um

Já existem dezenas de servidores MCP para Android, quase todos em Python ou TypeScript. O que falta neles é governança. Nenhum dos que analisei bloqueia app, pede confirmação antes de uma ação destrutiva ou registra o que a IA fez.

Aqui isso mora no servidor. Um texto malicioso numa página que a IA leia no celular não consegue mudar a política, porque nenhuma ferramenta mexe nela.

## Dois modos

| | Modo padrão | Modo god |
|---|---|---|
| Para quem | Quem baixa o projeto | Quem sabe o que tem no aparelho |
| Apps negados | WhatsApp, WhatsApp Business e Instagram | Nenhum, salvo os que você listar |
| Ação destrutiva | O humano confirma no próprio cliente MCP | Livre |
| `shell` livre | Não existe | Existe |
| URL que não é http(s) | Recusada | Aceita |
| Registro de cada ação | Sim | Sim |
| Uma sessão por vez | Sim | Sim |
| Cortar os fios | Sim | Sim |

**App negado** significa que a IA não toca, não digita e não desliza nele. Ela pode ler a tela e pode sair dele com as teclas `voltar`, `inicio` e `recentes`. Se um toque ou um link abrir um app negado, o servidor volta para a tela inicial na hora e avisa.

**O modo vem só da configuração da sua máquina.** Nenhuma ferramenta o altera.

## Ferramentas

| Ferramenta | O que faz | Tipo |
|---|---|---|
| `estado` | Modo, apps negados, fios, trava, aparelho, app na frente | leitura |
| `listar_dispositivos` | Aparelhos que o adb enxerga e o estado de cada um | leitura |
| `ler_tela` | Elementos úteis da tela, numerados, com tipo, texto, id, coordenadas e o que aceitam | leitura |
| `capturar_tela` | Foto da tela, reduzida, com o fator para converter coordenadas | leitura |
| `esperar_texto` | Lê a tela até um texto aparecer ou o tempo acabar | leitura |
| `listar_apps` | Pacotes instalados, marcando os negados | leitura |
| `tocar` | Toca por número da leitura, por texto ou por coordenada; toque longo opcional | ação |
| `deslizar` | Por direção ou por coordenadas | ação |
| `digitar` | Digita no campo com foco; Enter opcional | ação |
| `tecla` | voltar, inicio, recentes, enter, apagar e outras | ação |
| `abrir_app` | Abre pelo nome do pacote | ação |
| `abrir_url` | Abre no navegador ou no app dono do link | ação |
| `fechar_app` | Força a parada, sem apagar dados | ação |
| `desinstalar_app` | Desinstala; no modo padrão, o humano confirma | destrutiva |
| `shell` | Shell do Android; só no modo god | destrutiva |
| `cortar_fios` | Desliga tudo até o humano religar | segurança |

### Como a IA enxerga a tela

`ler_tela` lê a árvore de acessibilidade pelo `uiautomator` e devolve só o que importa. Trecho real, de uma página aberta no Chrome:

```
App na frente: com.android.chrome
43 elementos (número entre colchetes serve para tocar; coordenadas são o centro, em pixels do aparelho):
[1] web (540,1326) rola foco
[3] item "Entrar" (728,320) toca
[9] item "Ver o produto" (540,1381) toca
[40] campo "bolaalta.com.br" #url_bar (465,165) toca digita
```

Uma tela comum tem dezenas de KB de XML, quase tudo contêiner vazio. Aqui entra só o que tem texto ou aceita ação. Um botão sem texto herda o texto dos filhos, e o texto que só repete o rótulo do botão não aparece duas vezes. Quando a árvore vem vazia, como em jogos e apps Flutter, a IA usa `capturar_tela`.

## Travas

- **Confirmação humana pelo protocolo.** No modo padrão, `desinstalar_app` pausa e pede ao cliente MCP que pergunte ao usuário, pelo recurso de *elicitation*. Quem responde é a pessoa, não o modelo. Cliente sem esse recurso recebe recusa. O Claude Code suporta desde a versão 2.1.76; o Claude Desktop ainda não.
- **Uma sessão de IA por vez.** Cada cliente MCP sobe o seu processo. O primeiro a usar o celular fica com ele. Os outros recebem recusa até 5 minutos sem uso, ou até aquela sessão fechar.
- **Cortar os fios.** `cortar_fios` cria um arquivo, e enquanto ele existir toda ferramenta que mexe no aparelho recusa, em qualquer sessão. A IA pode cortar, mas não religar. Quem religa é você, apagando o arquivo.
- **Registro.** Cada chamada vira uma linha JSON com horário, sessão, modo, ferramenta, parâmetros, resultado e duração. O texto digitado entra só como tamanho, salvo configuração contrária.

## Instalação

Pré-requisitos: Java 21, Maven, o `adb` das Android Platform Tools e um celular com **Depuração USB** ligada e este computador autorizado.

```bash
git clone https://github.com/ronnyarruda20/marionete-god.git
cd marionete-god
mvn package
```

### No Claude Code

```bash
claude mcp add marionete \
  -e MARIONETE_ADB=/caminho/para/adb \
  -- java -jar /caminho/para/marionete-god/target/marionete-god.jar --spring.profiles.active=stdio
```

Para o modo god, acrescente `-e MARIONETE_MODO=god`. Para negar apps também no god, acrescente `-e MARIONETE_APPSNEGADOS=com.instagram.android,com.whatsapp`.

### Configuração

| Variável | Padrão | O que faz |
|---|---|---|
| `MARIONETE_MODO` | `padrao` | `padrao` ou `god` |
| `MARIONETE_ADB` | `adb` | Caminho do executável adb |
| `MARIONETE_SERIAL` | o único conectado | Serial do aparelho, quando há mais de um |
| `MARIONETE_APPSNEGADOS` | a lista do modo | Pacotes separados por vírgula; `nenhum` zera a lista |
| `MARIONETE_PASTA` | `~/.marionete-god` | Onde ficam registro, trava e o arquivo dos fios |
| `MARIONETE_TRAVAEXPIRAEM` | `5m` | Tempo sem uso até a trava de uma sessão expirar |
| `MARIONETE_REGISTRARTEXTO` | `false` | Grava o texto digitado no registro |

## O que o adb ensina

- **`adb devices` mente por cache.** Ele lista o aparelho como pronto enquanto todo comando real falha. O servidor confere com um `echo ok` de verdade.
- **Aparelhos Xiaomi bloqueiam toque por padrão.** Sem a opção "Depuração USB (Configurações de segurança)", todo toque falha com `INJECT_EVENTS`. O erro vem traduzido, com o caminho para ligar.
- **`input text` não digita acento.** Para digitar em português, instale o [ADBKeyboard](https://github.com/senzhk/ADBKeyBoard) no aparelho. O servidor troca o teclado, digita e devolve o teclado original.
- **Aspas e `$` quebram comandos na linha de comando do Windows.** Todo comando vai pelo stdin do `sh` do aparelho, e chega intacto.

## Limites conhecidos

- Ler a tela leva de 2 a 3 segundos, porque o `uiautomator` refaz a árvore a cada chamada. Um serviço residente no aparelho resolveria; está no roteiro.
- `abrir_url` abre uma aba nova a cada chamada no Chrome.
- Testado num Redmi 12 com Android 15 (HyperOS). Outros aparelhos devem funcionar, mas não foram medidos.
- Sem suporte a iOS.

## Stack

Java 21 · Spring Boot 4 · Spring AI 2.0 (MCP server, stdio e Streamable HTTP) · `adb`

## Licença

MIT
