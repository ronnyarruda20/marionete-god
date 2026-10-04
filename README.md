# marionete-god

> 🚧 **Em construção.** Ainda não há código neste repositório. O README descreve o que está sendo construído, não o que já funciona.

Servidor **MCP (Model Context Protocol)** em Java, com Spring AI, que dá a um assistente de IA controle real de um celular Android pelo `adb`: ler a tela, tocar, digitar, abrir apps e URLs.

A IA puxa os fios. O celular se mexe. Você decide até onde os fios alcançam.

## Por que mais um

Já existem dezenas de servidores MCP para Android, quase todos em Python ou TypeScript. O que falta neles é governança: nenhum dos que analisei bloqueia app, pede confirmação antes de uma ação destrutiva ou registra o que a IA fez.

Este projeto nasce com isso no servidor, não no prompt.

## Dois modos

| | Modo padrão | Modo god |
|---|---|---|
| Para quem | Quem baixa o projeto | Quem sabe o que tem no aparelho |
| Apps | Lista de negados por padrão | Tudo liberado |
| Ações destrutivas | Pedem confirmação humana | Livres |
| Shell `adb` livre | Desligado | Ligado |
| Registro de cada ação | Ligado | Ligado |
| Cortar os fios (desligar na hora) | Ligado | Ligado |

O modo é escolhido na configuração da sua máquina. Nenhuma ferramenta do servidor deixa a IA se promover a modo god.

## Planejado para a v1

- Ler a tela pela árvore de acessibilidade (`uiautomator`), com captura de tela quando a árvore não basta.
- Tocar, deslizar e digitar.
- Os dois modos, com registro de ações e botão de desligar.
- Uma sessão de IA por vez no aparelho.

## Stack

Java 21 · Spring Boot · Spring AI (MCP server) · `adb`

## Licença

MIT
