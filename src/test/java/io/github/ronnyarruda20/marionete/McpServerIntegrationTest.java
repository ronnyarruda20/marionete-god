package io.github.ronnyarruda20.marionete;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.ronnyarruda20.marionete.adb.Adb;
import io.github.ronnyarruda20.marionete.adb.AdbFalso;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Sobe o servidor de verdade, no modo padrão, e conversa com ele por um cliente MCP. O adb é falso:
 * nenhum aparelho é necessário.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerIntegrationTest {

    @TempDir
    static Path pasta;

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("marionete.pasta", () -> pasta.toString());
        registro.add("marionete.modo", () -> "padrao");
    }

    @TestConfiguration
    static class AparelhoFalso {

        @Bean
        @Primary
        AdbFalso adbFalso() throws IOException {
            String xml;
            try (var in = McpServerIntegrationTest.class.getResourceAsStream("/tela/login.xml")) {
                xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            BufferedImage tela = new BufferedImage(1080, 2400, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(tela, "png", png);
            return new AdbFalso()
                    .appNaFrente("com.exemplo.app")
                    .responder("exec-out uiautomator dump", xml)
                    .responderBytes("exec-out screencap", png.toByteArray())
                    .responder("pm uninstall", "Success\n");
        }
    }

    @LocalServerPort
    int porta;

    @Autowired
    AdbFalso adb;

    McpSyncClient cliente;

    McpSyncClient conectar(Function<McpSchema.ElicitFormRequest, McpSchema.ElicitResult> humano) {
        var transporte = HttpClientStreamableHttpTransport.builder("http://localhost:" + porta).build();
        var especificacao = McpClient.sync(transporte).requestTimeout(Duration.ofSeconds(20));
        if (humano != null) {
            especificacao.capabilities(McpSchema.ClientCapabilities.builder().elicitation().build())
                    .elicitation(humano);
        }
        cliente = especificacao.build();
        cliente.initialize();
        return cliente;
    }

    @AfterEach
    void desconectar() {
        if (cliente != null) {
            cliente.closeGracefully();
        }
    }

    static String texto(McpSchema.CallToolResult resultado) {
        return resultado.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(c -> ((McpSchema.TextContent) c).text())
                .reduce("", String::concat);
    }

    McpSchema.CallToolResult chamar(String ferramenta, Map<String, Object> argumentos) {
        return cliente.callTool(new McpSchema.CallToolRequest(ferramenta, argumentos));
    }

    @Test
    void expoeAsFerramentasComAsDicasDeLeituraEDestruicao() {
        conectar(null);
        List<McpSchema.Tool> ferramentas = cliente.listTools().tools();

        assertThat(ferramentas).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder(
                "estado", "listar_dispositivos", "cortar_fios", "ler_tela", "capturar_tela", "esperar_texto",
                "listar_apps", "tocar", "deslizar", "digitar", "tecla", "abrir_app", "abrir_url", "fechar_app",
                "desinstalar_app", "shell");
        assertThat(ferramentas).filteredOn(t -> t.name().equals("ler_tela")).singleElement()
                .satisfies(t -> assertThat(t.annotations().readOnlyHint()).isTrue());
        assertThat(ferramentas).filteredOn(t -> t.name().equals("desinstalar_app")).singleElement()
                .satisfies(t -> {
                    assertThat(t.annotations().destructiveHint()).isTrue();
                    assertThat(t.inputSchema()).extractingByKey("properties")
                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                            .containsOnlyKeys("pacote");
                });
    }

    @Test
    void leATelaEDepoisTocaPeloNumero() {
        conectar(null);

        var leitura = chamar("ler_tela", Map.of());
        assertThat(leitura.isError()).isFalse();
        assertThat(texto(leitura)).contains("App na frente: com.exemplo.app").contains("[5] item \"Entrar\"");

        var toque = chamar("tocar", Map.of("ref", 5));
        assertThat(texto(toque)).contains("Toquei em [5] item \"Entrar\"");
        assertThat(adb.shells()).contains("input tap 540 920");
    }

    @Test
    void capturaVoltaComoImagemReduzidaComOFatorDeConversao() {
        conectar(null);

        var captura = chamar("capturar_tela", Map.of());

        assertThat(captura.content()).anySatisfy(c -> {
            assertThat(c).isInstanceOf(McpSchema.ImageContent.class);
            assertThat(((McpSchema.ImageContent) c).mimeType()).isEqualTo("image/jpeg");
        });
        assertThat(texto(captura)).contains("1080x2400 reduzida para 540x1200").contains("multiplique x e y por 2.000");
    }

    @Test
    void shellNaoExisteNoModoPadrao() {
        conectar(null);

        var resultado = chamar("shell", Map.of("comando", "id"));

        assertThat(resultado.isError()).isTrue();
        assertThat(resultado.content()).hasSize(1);
        assertThat(texto(resultado)).startsWith("Recusado: 'shell' só existe no modo god");
        assertThat(adb.shells()).doesNotContain("id");
    }

    @Test
    void desinstalarPerguntaAoHumanoPeloProtocoloEObedeceOSim() {
        conectar(pedido -> {
            assertThat(pedido.message()).contains("DESINSTALAR com.exemplo.lixo");
            return new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of("confirmar", true));
        });

        var resultado = chamar("desinstalar_app", Map.of("pacote", "com.exemplo.lixo"));

        assertThat(texto(resultado)).isEqualTo("Desinstalei com.exemplo.lixo.");
        assertThat(adb.shells()).contains("pm uninstall 'com.exemplo.lixo'");
    }

    @Test
    void desinstalarObedeceONao() {
        conectar(pedido -> new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.DECLINE, null));

        var resultado = chamar("desinstalar_app", Map.of("pacote", "com.exemplo.recusado"));

        assertThat(resultado.isError()).isTrue();
        assertThat(texto(resultado)).contains("não confirmou");
        assertThat(adb.shells()).doesNotContain("pm uninstall 'com.exemplo.recusado'");
    }

    @Test
    void clienteQueNaoSabePerguntarNaoDesinstala() {
        conectar(null);

        var resultado = chamar("desinstalar_app", Map.of("pacote", "com.exemplo.semcliente"));

        assertThat(resultado.isError()).isTrue();
        assertThat(texto(resultado)).contains("elicitation");
        assertThat(adb.shells()).doesNotContain("pm uninstall 'com.exemplo.semcliente'");
    }

    @Test
    void cadaChamadaDeixaUmaLinhaNoRegistro() throws IOException {
        conectar(null);
        chamar("listar_dispositivos", Map.of());

        assertThat(Files.readAllLines(pasta.resolve("registro.jsonl")))
                .anyMatch(l -> l.contains("\"ferramenta\":\"listar_dispositivos\""));
    }
}
