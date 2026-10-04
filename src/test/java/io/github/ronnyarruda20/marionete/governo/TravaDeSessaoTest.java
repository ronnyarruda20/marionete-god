package io.github.ronnyarruda20.marionete.governo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TravaDeSessaoTest {

    @TempDir
    Path pasta;

    Instant t0 = Instant.parse("2026-10-04T12:00:00Z");
    Set<Long> vivos = Set.of(1L, 2L);

    TravaDeSessao sessao(long pid, Instant agora) {
        return new TravaDeSessao(pasta, Duration.ofMinutes(5), Clock.fixed(agora, ZoneOffset.UTC), pid, vivos::contains);
    }

    @Test
    void segundaSessaoVivaERecenteERecusada() {
        sessao(1, t0).pegar();

        assertThatThrownBy(() -> sessao(2, t0.plusSeconds(60)).pegar())
                .isInstanceOf(Recusa.class)
                .hasMessageContaining("outra sessão de IA (processo 1")
                .hasMessageContaining("libera sozinho em até 5 min");
    }

    @Test
    void travaSemUsoExpiraSozinha() {
        sessao(1, t0).pegar();

        assertThatCode(() -> sessao(2, t0.plus(Duration.ofMinutes(6))).pegar()).doesNotThrowAnyException();
        assertThat(sessao(2, t0.plus(Duration.ofMinutes(6))).descrever()).startsWith("com esta sessão");
    }

    @Test
    void donoQueMorreuNaoSeguraOCelular() {
        sessao(99, t0).pegar();

        assertThatCode(() -> sessao(2, t0.plusSeconds(10)).pegar()).doesNotThrowAnyException();
    }

    @Test
    void usoRenovaOPrazoDaPropriaSessao() {
        sessao(1, t0).pegar();
        sessao(1, t0.plus(Duration.ofMinutes(4))).pegar();

        assertThatThrownBy(() -> sessao(2, t0.plus(Duration.ofMinutes(8))).pegar()).isInstanceOf(Recusa.class);
    }

    @Test
    void soltarLiberaParaAOutra() {
        sessao(1, t0).pegar();
        sessao(1, t0).soltar();

        assertThatCode(() -> sessao(2, t0.plusSeconds(1)).pegar()).doesNotThrowAnyException();
    }
}
