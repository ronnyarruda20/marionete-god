package io.github.ronnyarruda20.marionete.governo;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import io.github.ronnyarruda20.marionete.adb.AdbException;
import io.github.ronnyarruda20.marionete.adb.Celular;
import io.github.ronnyarruda20.marionete.config.Modo;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Toda ferramenta passa por aqui, nesta ordem:
 *
 * <ol>
 * <li>fios cortados? recusa;</li>
 * <li>só no god e o modo é padrão? recusa;</li>
 * <li>trava entre sessões;</li>
 * <li>app alvo, ou app na frente, negado? recusa;</li>
 * <li>destrutiva no modo padrão? pergunta ao humano;</li>
 * <li>executa;</li>
 * <li>a ação levou a um app negado? volta para a tela inicial e avisa;</li>
 * <li>registra, aconteça o que acontecer.</li>
 * </ol>
 */
public class Guarda {

    /** O que a ferramenta quer fazer, para a governança decidir. */
    public record Pedido(String ferramenta, Acao acao, Map<String, Object> parametros, String pacoteAlvo,
            String pergunta) {

        public static Pedido de(String ferramenta, Acao acao, Map<String, Object> parametros) {
            return new Pedido(ferramenta, acao, parametros, null, null);
        }

        public Pedido comAlvo(String pacote) {
            return new Pedido(ferramenta, acao, parametros, pacote, pergunta);
        }

        public Pedido perguntando(String texto) {
            return new Pedido(ferramenta, acao, parametros, pacoteAlvo, texto);
        }
    }

    private final Modo modo;
    private final List<String> negados;
    private final Celular celular;
    private final Fios fios;
    private final TravaDeSessao trava;
    private final Registro registro;
    private final Confirmador confirmador;

    public Guarda(Modo modo, List<String> negados, Celular celular, Fios fios, TravaDeSessao trava,
            Registro registro, Confirmador confirmador) {
        this.modo = modo;
        this.negados = List.copyOf(negados);
        this.celular = celular;
        this.fios = fios;
        this.trava = trava;
        this.registro = registro;
        this.confirmador = confirmador;
    }

    public Modo modo() {
        return modo;
    }

    public List<String> negados() {
        return negados;
    }

    public boolean negado(String pacote) {
        return pacote != null && negados.contains(pacote);
    }

    public <T> T executar(Pedido pedido, McpSyncRequestContext contexto, Supplier<T> corpo) {
        long inicio = System.nanoTime();
        try {
            T resultado = governar(pedido, contexto, corpo);
            anotar(pedido, "ok", null, inicio);
            return resultado;
        } catch (Recusa r) {
            anotar(pedido, "recusado", r.getMessage(), inicio);
            throw r;
        } catch (RuntimeException e) {
            anotar(pedido, "erro", e.getMessage(), inicio);
            throw e;
        }
    }

    /**
     * Como {@link #executar}, mas já no formato do MCP: texto vira conteúdo de texto, e qualquer falha
     * (recusa, adb, argumento) vira um resultado de erro com UMA mensagem legível, sem pilha nem prefixo.
     */
    public McpSchema.CallToolResult responder(Pedido pedido, McpSyncRequestContext contexto, Supplier<?> corpo) {
        try {
            Object resultado = executar(pedido, contexto, corpo);
            if (resultado instanceof McpSchema.CallToolResult pronto) {
                return pronto;
            }
            return McpSchema.CallToolResult.builder().addTextContent(String.valueOf(resultado)).build();
        } catch (RuntimeException e) {
            String mensagem = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return McpSchema.CallToolResult.builder().addTextContent(mensagem).isError(true).build();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T governar(Pedido pedido, McpSyncRequestContext contexto, Supplier<T> corpo) {
        Acao acao = pedido.acao();
        if (acao.usaAparelho) {
            fios.exigirLigados();
        }
        if (acao.soNoGod && modo != Modo.GOD) {
            throw new Recusa("'" + pedido.ferramenta() + "' só existe no modo god, e este servidor roda no modo "
                    + "padrão. O modo é escolhido pelo humano na configuração (MARIONETE_MODO), não por ferramenta.");
        }
        if (acao.usaAparelho) {
            trava.pegar();
        }
        if (negado(pedido.pacoteAlvo())) {
            throw new Recusa("o app " + pedido.pacoteAlvo() + " está na lista de negados desta máquina.");
        }
        if (acao.ageNaTela) {
            String naFrente = celular.appEmPrimeiroPlano();
            if (negado(naFrente)) {
                throw new Recusa("o app na frente (" + naFrente + ") está na lista de negados. A IA pode sair dele "
                        + "com tecla 'inicio' ou 'voltar', mas não tocar nem digitar nele.");
            }
        }
        if (acao.destrutiva && modo == Modo.PADRAO) {
            String pergunta = pedido.pergunta() != null ? pedido.pergunta()
                    : "A IA quer executar '" + pedido.ferramenta() + "' no celular com " + pedido.parametros() + ".";
            if (!confirmador.confirmar(contexto, pergunta)) {
                throw new Recusa("o humano não confirmou '" + pedido.ferramenta() + "'.");
            }
        }

        T resultado = corpo.get();

        if (acao.ageNaTela || acao == Acao.ABRIR) {
            String aviso = conferirDepois();
            if (aviso != null && resultado instanceof String texto) {
                return (T) (texto + "\n" + aviso);
            }
        }
        return resultado;
    }

    /** Um toque ou um link pode abrir um app negado. Se abriu, sai dele na hora. */
    private String conferirDepois() {
        try {
            String naFrente = celular.appEmPrimeiroPlano();
            if (negado(naFrente)) {
                celular.tecla("KEYCODE_HOME");
                return "⚠️ A ação levou ao app " + naFrente + ", que está na lista de negados. Voltei para a tela "
                        + "inicial.";
            }
        } catch (AdbException e) {
            return "⚠️ Não consegui conferir qual app ficou na frente: " + e.getMessage();
        }
        return null;
    }

    private void anotar(Pedido pedido, String resultado, String detalhe, long inicio) {
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        registro.anotar(modo.name().toLowerCase(), pedido.ferramenta(), pedido.parametros(), resultado, detalhe, ms);
    }
}
