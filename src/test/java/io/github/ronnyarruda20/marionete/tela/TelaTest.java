package io.github.ronnyarruda20.marionete.tela;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TelaTest {

    static Elemento el(int ref, String rotulo, String id, boolean clicavel) {
        return new Elemento(ref, clicavel ? "botão" : "texto", rotulo, id, 0, 0, 10, 10,
                clicavel, false, false, false, false, true, false, false);
    }

    Tela tela = new Tela("com.exemplo", List.of(
            el(1, "Configurações", "", false),
            el(2, "Configurações", "abrir_config", true),
            el(3, "Sair da conta", "sair", true)));

    @Test
    void achaSemAcentoNemCaixaEPrefereOQueAceitaToque() {
        assertThat(tela.porTexto("configuracoes")).get().extracting(Elemento::ref).isEqualTo(2);
    }

    @Test
    void achaPeloIdQuandoOTextoNaoBate() {
        assertThat(tela.porTexto("sair")).get().extracting(Elemento::ref).isEqualTo(3);
    }

    @Test
    void achaPorTrechoQuandoNaoHaIgual() {
        assertThat(tela.porTexto("da conta")).get().extracting(Elemento::ref).isEqualTo(3);
    }

    @Test
    void telaVaziaSugereACaptura() {
        assertThat(new Tela("com.jogo", List.of()).formatar()).contains("capturar_tela");
    }
}
