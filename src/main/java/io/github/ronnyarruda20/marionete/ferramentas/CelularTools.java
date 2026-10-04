package io.github.ronnyarruda20.marionete.ferramentas;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;

import io.github.ronnyarruda20.marionete.adb.AdbException;
import io.github.ronnyarruda20.marionete.adb.Celular;
import io.github.ronnyarruda20.marionete.config.MarioneteProperties;
import io.github.ronnyarruda20.marionete.config.Modo;
import io.github.ronnyarruda20.marionete.governo.Acao;
import io.github.ronnyarruda20.marionete.governo.Fios;
import io.github.ronnyarruda20.marionete.governo.Guarda;
import io.github.ronnyarruda20.marionete.governo.Guarda.Pedido;
import io.github.ronnyarruda20.marionete.governo.Recusa;
import io.github.ronnyarruda20.marionete.governo.TravaDeSessao;
import io.github.ronnyarruda20.marionete.tela.Elemento;
import io.github.ronnyarruda20.marionete.tela.Tela;
import io.github.ronnyarruda20.marionete.tela.Teclas;
import io.modelcontextprotocol.spec.McpSchema;

@Component
public class CelularTools {

    private final Celular celular;
    private final Guarda guarda;
    private final Fios fios;
    private final TravaDeSessao trava;
    private final MarioneteProperties props;

    public CelularTools(Celular celular, Guarda guarda, Fios fios, TravaDeSessao trava, MarioneteProperties props) {
        this.celular = celular;
        this.guarda = guarda;
        this.fios = fios;
        this.trava = trava;
        this.props = props;
    }

    // ================================================================ estado

    @McpTool(name = "estado",
            title = "Estado da marionete",
            description = """
                    Mostra o modo (padrão ou god), os apps negados, se os fios estão cortados, quem está com a \
                    trava do celular, qual aparelho está em uso, se ele responde e qual app está na frente. Use \
                    no começo de uma tarefa ou quando algo falhar sem motivo claro.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public McpSchema.CallToolResult estado() {
        return guarda.responder(Pedido.de("estado", Acao.CONSULTA, Map.of()), null, () -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Modo: ").append(guarda.modo() == Modo.GOD ? "god" : "padrão").append('\n');
            sb.append("Apps negados: ").append(guarda.negados().isEmpty() ? "nenhum" : String.join(", ", guarda.negados()))
                    .append('\n');
            sb.append("Fios: ").append(fios.motivoDoCorte().map(m -> "CORTADOS (" + m + ")").orElse("ligados")).append('\n');
            sb.append("Trava: ").append(trava.descrever()).append('\n');
            try {
                String serial = celular.serial();
                sb.append("Aparelho: ").append(serial);
                boolean responde = celular.responde();
                sb.append(responde ? " (responde)" : " (NÃO responde ao shell)").append('\n');
                if (responde) {
                    int[] t = celular.tamanhoDaTela();
                    sb.append("Tela: ").append(t[0]).append('x').append(t[1]).append('\n');
                    sb.append("App na frente: ").append(celular.appEmPrimeiroPlano()).append('\n');
                    sb.append("Digitação com acento: ")
                            .append(celular.adbKeyboardInstalado() ? "sim (ADBKeyboard)" : "não (instale o ADBKeyboard)")
                            .append('\n');
                }
            } catch (AdbException e) {
                sb.append("Aparelho: indisponível. ").append(e.getMessage()).append('\n');
            }
            return sb.toString().stripTrailing();
        });
    }

    @McpTool(name = "listar_dispositivos",
            title = "Listar aparelhos",
            description = "Lista os aparelhos que o adb enxerga, com o estado de cada um (device, offline, unauthorized).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public McpSchema.CallToolResult listarDispositivos() {
        return guarda.responder(Pedido.de("listar_dispositivos", Acao.CONSULTA, Map.of()), null, () -> {
            List<Celular.Dispositivo> lista = celular.dispositivos();
            if (lista.isEmpty()) {
                return "Nenhum aparelho. Conecte por USB ou rode adb connect ip:porta.";
            }
            return lista.stream()
                    .map(d -> d.serial() + "  " + d.estado() + (d.modelo().isEmpty() ? "" : "  " + d.modelo()))
                    .collect(Collectors.joining("\n"));
        });
    }

    @McpTool(name = "cortar_fios",
            title = "Cortar os fios",
            description = """
                    Botão de desligar: a partir daqui, toda ferramenta que mexe no celular recusa, nesta e em \
                    qualquer outra sessão. Use se perceber que está fazendo algo errado ou se o usuário pedir para \
                    parar. Só o humano religa, apagando um arquivo na máquina dele.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public McpSchema.CallToolResult cortarFios(@McpToolParam(description = "Por que os fios estão sendo cortados.") String motivo) {
        String m = (motivo == null || motivo.isBlank()) ? "sem motivo informado" : motivo.strip();
        return guarda.responder(Pedido.de("cortar_fios", Acao.CONSULTA, Map.of("motivo", m)), null, () -> {
            fios.cortar(m);
            return "Fios cortados. Nenhuma ferramenta mexe mais no celular até o humano apagar " + fios.arquivo() + ".";
        });
    }

    // ================================================================ leitura

    @McpTool(name = "ler_tela",
            title = "Ler a tela",
            description = """
                    Lê a tela do celular pela árvore de acessibilidade e devolve os elementos úteis, numerados, com \
                    tipo, texto, id, coordenadas do centro e o que aceitam (toca, digita, rola). É a forma barata de \
                    enxergar: use antes de agir e depois de cada ação. Leva de 2 a 3 segundos.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public McpSchema.CallToolResult lerTela() {
        return guarda.responder(Pedido.de("ler_tela", Acao.LEITURA, Map.of()), null, () -> celular.lerTela().formatar());
    }

    @McpTool(name = "capturar_tela",
            title = "Capturar a tela",
            description = """
                    Tira uma foto da tela e devolve como imagem. Use quando ler_tela vier vazia ou não bastar \
                    (jogos, Flutter, vídeo, imagem). A imagem vem reduzida; as coordenadas para tocar são as do \
                    aparelho, e a resposta diz o fator de conversão.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public McpSchema.CallToolResult capturarTela(
            @McpToolParam(required = false, description = "Largura da imagem devolvida, entre 240 e 1080. Padrão: 540.")
            Integer largura) {
        int alvo = largura == null ? 540 : Math.max(240, Math.min(1080, largura));
        return guarda.responder(Pedido.de("capturar_tela", Acao.LEITURA, Map.of("largura", alvo)), null, () -> {
            BufferedImage original = lerPng(celular.capturarTela());
            double fator = (double) original.getWidth() / alvo;
            int altura = (int) Math.round(original.getHeight() / fator);
            String jpeg = Base64.getEncoder().encodeToString(jpeg(reduzir(original, alvo, altura)));
            String nota = String.format(java.util.Locale.ROOT,
                    "Tela de %dx%d reduzida para %dx%d. Para tocar num ponto da imagem, multiplique x e y por %.3f.",
                    original.getWidth(), original.getHeight(), alvo, altura, fator);
            return McpSchema.CallToolResult.builder()
                    .addContent(new McpSchema.ImageContent(null, jpeg, "image/jpeg"))
                    .addTextContent(nota)
                    .build();
        });
    }

    @McpTool(name = "esperar_texto",
            title = "Esperar um texto aparecer",
            description = """
                    Lê a tela repetidamente até aparecer um texto (sem diferenciar acento nem maiúscula) ou o tempo \
                    acabar. Use depois de abrir um app, enviar um formulário ou carregar uma página, em vez de chutar \
                    um tempo de espera.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public McpSchema.CallToolResult esperarTexto(
            @McpToolParam(description = "Texto que deve aparecer na tela.") String texto,
            @McpToolParam(required = false, description = "Limite em segundos, entre 3 e 60. Padrão: 15.") Integer segundos) {
        int limite = segundos == null ? 15 : Math.max(3, Math.min(60, segundos));
        return guarda.responder(Pedido.de("esperar_texto", Acao.LEITURA, parametros("texto", texto, "segundos", limite)),
                null, () -> {
                    long fim = System.nanoTime() + Duration.ofSeconds(limite).toNanos();
                    long inicio = System.nanoTime();
                    Tela tela;
                    do {
                        tela = celular.lerTela();
                        if (tela.contemTexto(texto)) {
                            long ms = (System.nanoTime() - inicio) / 1_000_000;
                            return "Apareceu \"" + texto + "\" em " + ms + " ms.\n\n" + tela.formatar();
                        }
                    } while (System.nanoTime() < fim);
                    return "\"" + texto + "\" não apareceu em " + limite + " s. Última leitura:\n\n" + tela.formatar();
                });
    }

    @McpTool(name = "listar_apps",
            title = "Listar apps instalados",
            description = "Lista os pacotes instalados (por padrão, só os que o usuário instalou), para usar em abrir_app.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public McpSchema.CallToolResult listarApps(
            @McpToolParam(required = false, description = "Filtra pelo trecho do nome do pacote. Ex.: chrome") String filtro,
            @McpToolParam(required = false, description = "Inclui os apps do sistema. Padrão: não.") Boolean incluirSistema) {
        boolean sistema = Boolean.TRUE.equals(incluirSistema);
        return guarda.responder(Pedido.de("listar_apps", Acao.LEITURA, parametros("filtro", filtro, "incluir_sistema", sistema)),
                null, () -> {
                    List<String> apps = celular.apps(sistema).stream()
                            .filter(p -> filtro == null || filtro.isBlank() || p.contains(filtro.strip().toLowerCase()))
                            .map(p -> guarda.negado(p) ? p + "  (negado)" : p)
                            .toList();
                    return apps.isEmpty() ? "Nenhum app encontrado." : apps.size() + " apps:\n" + String.join("\n", apps);
                });
    }

    // ================================================================ ação na tela

    @McpTool(name = "tocar",
            title = "Tocar na tela",
            description = """
                    Toca num elemento. Informe UM destes: ref (o número de ler_tela, o jeito mais seguro), texto \
                    (procura na tela na hora) ou x e y (pixels do aparelho). Com longo=true, faz um toque longo.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false,
                    openWorldHint = true))
    public McpSchema.CallToolResult tocar(
            @McpToolParam(required = false, description = "Número do elemento na última ler_tela.") Integer ref,
            @McpToolParam(required = false, description = "Texto, descrição ou id do elemento.") String texto,
            @McpToolParam(required = false, description = "Coordenada x em pixels do aparelho.") Integer x,
            @McpToolParam(required = false, description = "Coordenada y em pixels do aparelho.") Integer y,
            @McpToolParam(required = false, description = "Toque longo (segurar). Padrão: não.") Boolean longo) {
        Map<String, Object> p = parametros("ref", ref, "texto", texto, "x", x, "y", y, "longo", longo);
        return guarda.responder(Pedido.de("tocar", Acao.TOQUE, p), null, () -> {
            Elemento alvo = null;
            int px;
            int py;
            if (ref != null) {
                Tela tela = celular.ultimaTela();
                if (tela == null) {
                    throw new IllegalArgumentException("A tela mudou desde a última leitura (ou nunca foi lida). "
                            + "Rode ler_tela e use o número novo.");
                }
                alvo = tela.porRef(ref).orElseThrow(() -> new IllegalArgumentException(
                        "Não há elemento [" + ref + "] na última leitura."));
                px = alvo.centroX();
                py = alvo.centroY();
            } else if (texto != null && !texto.isBlank()) {
                Tela tela = celular.lerTela();
                alvo = tela.porTexto(texto).orElseThrow(() -> new IllegalArgumentException(
                        "Não achei \"" + texto + "\" na tela. Rode ler_tela para ver o que existe."));
                px = alvo.centroX();
                py = alvo.centroY();
            } else if (x != null && y != null) {
                px = x;
                py = y;
            } else {
                throw new IllegalArgumentException("Informe ref, texto, ou x e y.");
            }
            if (Boolean.TRUE.equals(longo)) {
                celular.tocarLongo(px, py, 800);
            } else {
                celular.tocar(px, py);
            }
            String oQue = alvo == null ? "(" + px + "," + py + ")" : alvo.linha();
            return (Boolean.TRUE.equals(longo) ? "Toque longo em " : "Toquei em ") + oQue
                    + ". Leia a tela de novo antes do próximo passo.";
        });
    }

    @McpTool(name = "deslizar",
            title = "Deslizar o dedo",
            description = """
                    Desliza o dedo na tela. Use direcao (cima, baixo, esquerda, direita: para onde o DEDO vai; para \
                    ver mais conteúdo abaixo, deslize para cima) ou as quatro coordenadas.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false,
                    openWorldHint = true))
    public McpSchema.CallToolResult deslizar(
            @McpToolParam(required = false, description = "cima, baixo, esquerda ou direita.") String direcao,
            @McpToolParam(required = false, description = "x inicial") Integer x1,
            @McpToolParam(required = false, description = "y inicial") Integer y1,
            @McpToolParam(required = false, description = "x final") Integer x2,
            @McpToolParam(required = false, description = "y final") Integer y2,
            @McpToolParam(required = false, description = "Duração em ms, entre 50 e 3000. Padrão: 300.") Integer duracaoMs) {
        int ms = duracaoMs == null ? 300 : Math.max(50, Math.min(3000, duracaoMs));
        Map<String, Object> p = parametros("direcao", direcao, "x1", x1, "y1", y1, "x2", x2, "y2", y2, "duracao_ms", ms);
        return guarda.responder(Pedido.de("deslizar", Acao.TOQUE, p), null, () -> {
            int[] c;
            if (x1 != null && y1 != null && x2 != null && y2 != null) {
                c = new int[] {x1, y1, x2, y2};
            } else if (direcao != null && !direcao.isBlank()) {
                int[] t = celular.tamanhoDaTela();
                int cx = t[0] / 2;
                int cy = t[1] / 2;
                int dx = t[0] * 35 / 100;
                int dy = t[1] * 30 / 100;
                c = switch (Teclas.normalizar(direcao)) {
                    case "cima" -> new int[] {cx, cy + dy, cx, cy - dy};
                    case "baixo" -> new int[] {cx, cy - dy, cx, cy + dy};
                    case "esquerda" -> new int[] {cx + dx, cy, cx - dx, cy};
                    case "direita" -> new int[] {cx - dx, cy, cx + dx, cy};
                    default -> throw new IllegalArgumentException("Direção inválida: '" + direcao
                            + "'. Use cima, baixo, esquerda ou direita.");
                };
            } else {
                throw new IllegalArgumentException("Informe direcao, ou x1, y1, x2 e y2.");
            }
            celular.deslizar(c[0], c[1], c[2], c[3], ms);
            return "Deslizei de (" + c[0] + "," + c[1] + ") para (" + c[2] + "," + c[3] + ") em " + ms + " ms.";
        });
    }

    @McpTool(name = "digitar",
            title = "Digitar",
            description = """
                    Digita no campo com foco (toque no campo antes). Texto com acento ou emoji exige o ADBKeyboard \
                    no aparelho; sem ele, só ASCII. Com enviar=true, aperta Enter no fim.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false,
                    openWorldHint = true))
    public McpSchema.CallToolResult digitar(
            @McpToolParam(description = "O texto a digitar.") String texto,
            @McpToolParam(required = false, description = "Aperta Enter depois. Padrão: não.") Boolean enviar) {
        String t = texto == null ? "" : texto;
        Object noRegistro = props.registrarTexto() ? t : "(" + t.length() + " caracteres)";
        Map<String, Object> p = parametros("texto", noRegistro, "enviar", enviar);
        return guarda.responder(Pedido.de("digitar", Acao.TOQUE, p), null, () -> {
            String como = celular.digitar(t);
            if (Boolean.TRUE.equals(enviar)) {
                celular.tecla(Teclas.CODIGOS.get("enter"));
            }
            return "Digitei " + t.length() + " caracteres (" + como + ")"
                    + (Boolean.TRUE.equals(enviar) ? " e apertei Enter." : ".");
        });
    }

    @McpTool(name = "tecla",
            title = "Apertar uma tecla",
            description = """
                    Aperta uma tecla do sistema. voltar, inicio e recentes funcionam até com um app negado na frente \
                    (servem para sair dele). Aceitas: voltar, inicio, recentes, enter, apagar, tab, esc, acordar, \
                    dormir, volume_mais, volume_menos, menu, buscar, cima, baixo, esquerda, direita.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false,
                    openWorldHint = true))
    public McpSchema.CallToolResult tecla(@McpToolParam(description = "Nome da tecla. Ex.: voltar") String nome) {
        String chave = Teclas.normalizar(nome == null ? "" : nome);
        String codigo = Teclas.CODIGOS.get(chave);
        Acao acao = Teclas.FUGA.contains(chave) ? Acao.FUGA : Acao.TOQUE;
        return guarda.responder(Pedido.de("tecla", acao, Map.of("nome", chave)), null, () -> {
            if (codigo == null) {
                throw new IllegalArgumentException("Tecla desconhecida: '" + nome + "'. Aceitas: " + Teclas.nomesAceitos());
            }
            celular.tecla(codigo);
            return "Apertei " + chave + ".";
        });
    }

    // ================================================================ apps

    @McpTool(name = "abrir_app",
            title = "Abrir um app",
            description = "Abre um app pelo nome do pacote (veja listar_apps). Ex.: com.android.chrome",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true,
                    openWorldHint = true))
    public McpSchema.CallToolResult abrirApp(@McpToolParam(description = "Nome do pacote. Ex.: com.android.chrome") String pacote) {
        String p = pacote == null ? "" : pacote.strip();
        return guarda.responder(Pedido.de("abrir_app", Acao.ABRIR, Map.of("pacote", p)).comAlvo(p), null, () -> {
            celular.abrirApp(p);
            return "Abri " + p + ". Use esperar_texto ou ler_tela para ver quando carregou.";
        });
    }

    @McpTool(name = "abrir_url",
            title = "Abrir uma URL",
            description = """
                    Abre uma URL no aparelho (navegador padrão, ou o app dono do link). No modo padrão, só http e \
                    https. Se o link abrir um app negado, a marionete sai dele e avisa.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true,
                    openWorldHint = true))
    public McpSchema.CallToolResult abrirUrl(@McpToolParam(description = "URL completa. Ex.: https://exemplo.com") String url) {
        String u = url == null ? "" : url.strip();
        return guarda.responder(Pedido.de("abrir_url", Acao.ABRIR, Map.of("url", u)), null, () -> {
            String esquema;
            try {
                esquema = URI.create(u).getScheme();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("URL inválida: " + u);
            }
            if (esquema == null) {
                throw new IllegalArgumentException("URL sem esquema: " + u + ". Ex.: https://" + u);
            }
            if (guarda.modo() == Modo.PADRAO && !esquema.equalsIgnoreCase("http") && !esquema.equalsIgnoreCase("https")) {
                throw new Recusa("no modo padrão só abro http e https; '" + esquema + ":' pode acionar app ou sistema.");
            }
            celular.abrirUrl(u);
            return "Abri " + u + ".";
        });
    }

    @McpTool(name = "fechar_app",
            title = "Fechar um app",
            description = "Força a parada de um app (como 'Forçar parada' nas configurações). Não apaga dados.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public McpSchema.CallToolResult fecharApp(@McpToolParam(description = "Nome do pacote.") String pacote) {
        String p = pacote == null ? "" : pacote.strip();
        return guarda.responder(Pedido.de("fechar_app", Acao.FECHAR, Map.of("pacote", p)), null, () -> {
            celular.fecharApp(p);
            return "Fechei " + p + ".";
        });
    }

    @McpTool(name = "desinstalar_app",
            title = "Desinstalar um app",
            description = """
                    Desinstala um app e apaga os dados dele no aparelho. No modo padrão, o humano confirma antes, \
                    pelo próprio cliente MCP.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    public McpSchema.CallToolResult desinstalarApp(
            @McpToolParam(description = "Nome do pacote.") String pacote,
            McpSyncRequestContext contexto) {
        String p = pacote == null ? "" : pacote.strip();
        Pedido pedido = Pedido.de("desinstalar_app", Acao.DESTRUTIVA, Map.of("pacote", p)).comAlvo(p)
                .perguntando("A IA quer DESINSTALAR " + p + " do celular, apagando os dados dele. Confirma?");
        return guarda.responder(pedido, contexto, () -> {
            String saida = celular.desinstalar(p);
            if (!saida.contains("Success")) {
                throw new AdbException("O aparelho não desinstalou " + p + ": " + saida);
            }
            return "Desinstalei " + p + ".";
        });
    }

    // ================================================================ god

    @McpTool(name = "shell",
            title = "Shell do aparelho (modo god)",
            description = """
                    Roda um comando no shell do Android (sh, como adb shell). Só existe no modo god. Não passa pela \
                    checagem de app negado: use com a mesma cautela de um terminal root.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false,
                    openWorldHint = true))
    public McpSchema.CallToolResult shell(
            @McpToolParam(description = "Comando. Ex.: dumpsys battery") String comando,
            @McpToolParam(required = false, description = "Limite em segundos, entre 1 e 120. Padrão: 30.") Integer segundos,
            McpSyncRequestContext contexto) {
        int limite = segundos == null ? 30 : Math.max(1, Math.min(120, segundos));
        String c = comando == null ? "" : comando;
        return guarda.responder(Pedido.de("shell", Acao.SHELL, parametros("comando", c, "segundos", limite)), contexto, () -> {
            String saida = celular.shell(c, Duration.ofSeconds(limite));
            String cortada = saida.length() > 20_000 ? saida.substring(0, 20_000) + "\n… (cortado em 20 mil caracteres)" : saida;
            return cortada.isBlank() ? "(sem saída)" : cortada.stripTrailing();
        });
    }

    // ================================================================ utilidades

    private static Map<String, Object> parametros(Object... chaveValor) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < chaveValor.length; i += 2) {
            if (chaveValor[i + 1] != null) {
                m.put((String) chaveValor[i], chaveValor[i + 1]);
            }
        }
        return m;
    }

    private static BufferedImage lerPng(byte[] png) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) {
                throw new AdbException("O aparelho devolveu uma captura ilegível (" + png.length + " bytes).");
            }
            return img;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage reduzir(BufferedImage original, int largura, int altura) {
        BufferedImage destino = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(original, 0, 0, largura, altura, null);
        g.dispose();
        return destino;
    }

    private static byte[] jpeg(BufferedImage imagem) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = escritor.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.8f);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream fluxo = new MemoryCacheImageOutputStream(saida)) {
            escritor.setOutput(fluxo);
            escritor.write(null, new IIOImage(imagem, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            escritor.dispose();
        }
        return saida.toByteArray();
    }
}
