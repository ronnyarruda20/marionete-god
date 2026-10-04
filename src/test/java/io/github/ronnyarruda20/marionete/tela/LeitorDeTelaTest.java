package io.github.ronnyarruda20.marionete.tela;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeitorDeTelaTest {

    List<Elemento> elementos;

    @BeforeEach
    void ler() throws IOException {
        try (var in = getClass().getResourceAsStream("/tela/login.xml")) {
            elementos = LeitorDeTela.ler(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void ficaSoComOQueTemTextoOuAceitaAcao() {
        assertThat(elementos).extracting(Elemento::linha).containsExactly(
                "[1] texto \"Bem-vindo de volta\" #titulo (540,250)",
                "[2] campo \"E-mail\" #email (540,460) toca digita foco",
                "[3] campo \"Senha\" #senha (540,620) toca digita senha",
                "[4] caixa \"Lembrar de mim\" #lembrar (330,760) toca marcado",
                "[5] item \"Entrar\" #entrar (540,920) toca",
                "[6] botão \"Criar conta\" (540,1080) toca desabilitado",
                "[7] lista #lista (540,1800) rola",
                "[8] item \"Configurações · Wi-Fi, Bluetooth\" (540,1400) toca");
    }

    @Test
    void botaoSemTextoHerdaOTextoDosFilhosEElesNaoSeRepetem() {
        assertThat(elementos).extracting(Elemento::rotulo)
                .contains("Entrar", "Configurações · Wi-Fi, Bluetooth")
                .doesNotContain("Wi-Fi, Bluetooth");
    }

    @Test
    void linkDePaginaWebComTextoProprioNaoRepeteOFilhoMasGuardaOQueForDiferente() {
        String web = "<hierarchy><node class='android.webkit.WebView' bounds='[0,0][1080,2000]'>"
                + "<node class='android.view.View' text='Entrar' clickable='true' bounds='[600,280][860,360]'>"
                + "<node class='android.widget.TextView' text='Entrar' bounds='[650,290][800,350]'/>"
                + "<node class='android.widget.TextView' text='novo' bounds='[800,290][850,350]'/>"
                + "</node></node></hierarchy>";

        assertThat(LeitorDeTela.ler(web)).extracting(Elemento::linha).containsExactly(
                "[1] item \"Entrar\" (730,320) toca",
                "[2] texto \"novo\" (825,320)");
    }

    @Test
    void elementoSemAreaNaoEntra() {
        assertThat(elementos).extracting(Elemento::rotulo).doesNotContain("Invisível");
    }

    @Test
    void ignoraOAvisoQueODumpEscreveDepoisDoXml() {
        assertThat(LeitorDeTela.recortarXml("lixo<?xml version='1.0'?><hierarchy></hierarchy>UI hierchary dumped"))
                .isEqualTo("<?xml version='1.0'?><hierarchy></hierarchy>");
    }

    @Test
    void saidaSemArvoreViraErroLegivel() {
        assertThatThrownBy(() -> LeitorDeTela.ler("ERROR: could not get idle state."))
                .hasMessageContaining("não devolveu a árvore")
                .hasMessageContaining("could not get idle state");
    }

    @Test
    void recusaDoctypeParaNaoAbrirPortaAXxe() {
        String malicioso = "<?xml version='1.0'?><!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]>"
                + "<hierarchy><node text='&e;' bounds='[0,0][1,1]'/></hierarchy>";
        assertThatThrownBy(() -> LeitorDeTela.ler(malicioso)).isInstanceOf(IllegalStateException.class);
    }
}
