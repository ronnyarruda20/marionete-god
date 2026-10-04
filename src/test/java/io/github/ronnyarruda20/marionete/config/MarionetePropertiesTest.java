package io.github.ronnyarruda20.marionete.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class MarionetePropertiesTest {

    static MarioneteProperties com(String modo, List<String> negados) {
        return new MarioneteProperties(modo, null, null, null, negados, null, null);
    }

    @Test
    void padraoNegaOsAppsQueCustamAConta() {
        assertThat(com("padrao", null).negadosEfetivos())
                .containsExactly("com.whatsapp", "com.whatsapp.w4b", "com.instagram.android");
    }

    @Test
    void godNaoNegaNadaPorPadrao() {
        assertThat(com("god", null).negadosEfetivos()).isEmpty();
    }

    @Test
    void listaExplicitaVenceOPadraoDoModo() {
        assertThat(com("god", List.of(" com.instagram.android ", "")).negadosEfetivos())
                .containsExactly("com.instagram.android");
        assertThat(com("padrao", List.of("nenhum")).negadosEfetivos()).isEmpty();
    }

    @Test
    void modoAusenteEPadraoEModoDesconhecidoNaoSobe() {
        assertThat(com(null, null).modoEfetivo()).isEqualTo(Modo.PADRAO);
        assertThat(com("GOD", null).modoEfetivo()).isEqualTo(Modo.GOD);
        assertThatThrownBy(() -> com("deus", null).modoEfetivo()).hasMessageContaining("'padrao' ou 'god'");
    }
}
