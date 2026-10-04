package io.github.ronnyarruda20.marionete.governo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * O botão de desligar. Enquanto o arquivo existir, toda ferramenta que mexe no aparelho recusa.
 *
 * <p>A IA pode cortar os fios (ferramenta {@code cortar_fios}), mas não pode religá-los: nenhuma
 * ferramenta apaga o arquivo. Quem religa é o humano, apagando-o na própria máquina.
 */
public class Fios {

    private final Path arquivo;
    private final Clock relogio;

    public Fios(Path pasta, Clock relogio) {
        this.arquivo = pasta.resolve("FIOS-CORTADOS.txt");
        this.relogio = relogio;
    }

    public Path arquivo() {
        return arquivo;
    }

    public Optional<String> motivoDoCorte() {
        if (!Files.exists(arquivo)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(arquivo, StandardCharsets.UTF_8).strip());
        } catch (IOException e) {
            return Optional.of("(arquivo ilegível)");
        }
    }

    public void cortar(String motivo) {
        try {
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, Instant.now(relogio) + " — " + motivo + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Não consegui cortar os fios em " + arquivo, e);
        }
    }

    public void exigirLigados() {
        motivoDoCorte().ifPresent(motivo -> {
            throw new Recusa("os fios estão cortados (" + motivo + "). Para religar, o humano apaga o arquivo "
                    + arquivo + ".");
        });
    }
}
