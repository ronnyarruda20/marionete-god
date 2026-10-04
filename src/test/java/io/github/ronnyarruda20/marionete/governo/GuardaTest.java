package io.github.ronnyarruda20.marionete.governo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import io.github.ronnyarruda20.marionete.adb.AdbFalso;
import io.github.ronnyarruda20.marionete.adb.Celular;
import io.github.ronnyarruda20.marionete.config.Modo;
import io.github.ronnyarruda20.marionete.governo.Guarda.Pedido;

class GuardaTest {

    @TempDir
    Path pasta;

    AdbFalso adb = new AdbFalso().appNaFrente("com.android.chrome");
    Celular celular = new Celular(adb, null);
    Clock relogio = Clock.systemUTC();
    Fios fios;
    Registro registro;
    AtomicInteger execucoes = new AtomicInteger();
    Boolean respostaDoHumano = true;

    Guarda guarda(Modo modo, List<String> negados) {
        fios = new Fios(pasta, relogio);
        registro = new Registro(pasta, relogio);
        Confirmador confirmador = new Confirmador() {
            @Override
            public boolean confirmar(McpSyncRequestContext contexto, String pergunta) {
                return respostaDoHumano;
            }
        };
        return new Guarda(modo, negados, celular, fios, new TravaDeSessao(pasta, Duration.ofMinutes(5), relogio),
                registro, confirmador);
    }

    String corpo() {
        execucoes.incrementAndGet();
        return "feito";
    }

    @Test
    void fiosCortadosBarramTudoQueMexeNoAparelhoMasNaoAConsulta() {
        Guarda g = guarda(Modo.GOD, List.of());
        fios.cortar("teste");

        assertThatThrownBy(() -> g.executar(Pedido.de("tocar", Acao.TOQUE, Map.of()), null, this::corpo))
                .isInstanceOf(Recusa.class).hasMessageContaining("fios estão cortados");
        assertThat(g.executar(Pedido.de("estado", Acao.CONSULTA, Map.of()), null, this::corpo)).isEqualTo("feito");
        assertThat(execucoes).hasValue(1);
    }

    @Test
    void shellSoExisteNoGod() {
        assertThatThrownBy(() -> guarda(Modo.PADRAO, List.of())
                .executar(Pedido.de("shell", Acao.SHELL, Map.of()), null, this::corpo))
                .isInstanceOf(Recusa.class).hasMessageContaining("só existe no modo god");

        assertThat(guarda(Modo.GOD, List.of()).executar(Pedido.de("shell", Acao.SHELL, Map.of()), null, this::corpo))
                .isEqualTo("feito");
    }

    @Test
    void appNegadoNaFrenteBarraToqueMasDeixaFugir() {
        adb.appNaFrente("com.instagram.android");
        Guarda g = guarda(Modo.GOD, List.of("com.instagram.android"));

        assertThatThrownBy(() -> g.executar(Pedido.de("tocar", Acao.TOQUE, Map.of()), null, this::corpo))
                .isInstanceOf(Recusa.class).hasMessageContaining("com.instagram.android");
        assertThat(g.executar(Pedido.de("tecla", Acao.FUGA, Map.of()), null, this::corpo)).isEqualTo("feito");
    }

    @Test
    void abrirAppNegadoERecusadoAntesDeExecutar() {
        Guarda g = guarda(Modo.PADRAO, List.of("com.whatsapp.w4b"));

        assertThatThrownBy(() -> g.executar(
                Pedido.de("abrir_app", Acao.ABRIR, Map.of()).comAlvo("com.whatsapp.w4b"), null, this::corpo))
                .isInstanceOf(Recusa.class);
        assertThat(execucoes).hasValue(0);
    }

    @Test
    void seAAcaoLevaAUmAppNegadoVoltaParaOInicio() {
        Guarda g = guarda(Modo.GOD, List.of("com.instagram.android"));

        String resultado = g.executar(Pedido.de("abrir_url", Acao.ABRIR, Map.of()), null, () -> {
            adb.appNaFrente("com.instagram.android"); // o link abriu o app
            return "Abri o link.";
        });

        assertThat(resultado).contains("Abri o link.").contains("Voltei para a tela inicial");
        assertThat(adb.shells()).contains("input keyevent KEYCODE_HOME");
    }

    @Test
    void destrutivaNoPadraoPerguntaAoHumano() {
        Guarda g = guarda(Modo.PADRAO, List.of());
        respostaDoHumano = false;

        assertThatThrownBy(() -> g.executar(Pedido.de("desinstalar_app", Acao.DESTRUTIVA, Map.of()), null, this::corpo))
                .isInstanceOf(Recusa.class).hasMessageContaining("não confirmou");
        assertThat(execucoes).hasValue(0);

        respostaDoHumano = true;
        assertThat(g.executar(Pedido.de("desinstalar_app", Acao.DESTRUTIVA, Map.of()), null, this::corpo))
                .isEqualTo("feito");
    }

    @Test
    void destrutivaNoGodNaoPergunta() {
        respostaDoHumano = false;

        assertThat(guarda(Modo.GOD, List.of())
                .executar(Pedido.de("desinstalar_app", Acao.DESTRUTIVA, Map.of()), null, this::corpo)).isEqualTo("feito");
    }

    @Test
    void registraSucessoRecusaEErro() throws IOException {
        Guarda g = guarda(Modo.PADRAO, List.of());
        g.executar(Pedido.de("ler_tela", Acao.LEITURA, Map.of()), null, this::corpo);
        try {
            g.executar(Pedido.de("shell", Acao.SHELL, Map.of("comando", "rm -rf /sdcard")), null, this::corpo);
        } catch (Recusa esperado) {
            // registrado abaixo
        }
        try {
            g.executar(Pedido.de("tocar", Acao.TOQUE, Map.of("x", 1)), null, () -> {
                throw new IllegalStateException("quebrou");
            });
        } catch (IllegalStateException esperado) {
            // registrado abaixo
        }

        List<String> linhas = Files.readAllLines(registro.arquivo());
        assertThat(linhas).hasSize(3);
        assertThat(linhas.get(0)).contains("\"ferramenta\":\"ler_tela\"", "\"resultado\":\"ok\"", "\"modo\":\"padrao\"");
        assertThat(linhas.get(1)).contains("\"ferramenta\":\"shell\"", "\"resultado\":\"recusado\"",
                "\"comando\":\"rm -rf /sdcard\"");
        assertThat(linhas.get(2)).contains("\"resultado\":\"erro\"", "quebrou");
    }
}
