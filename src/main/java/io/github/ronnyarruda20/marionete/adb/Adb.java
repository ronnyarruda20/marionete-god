package io.github.ronnyarruda20.marionete.adb;

import java.time.Duration;
import java.util.List;

/** Fronteira com o executável adb. Os testes usam uma implementação falsa. */
public interface Adb {

    /** Roda {@code adb [args]} sem aparelho escolhido (devices, connect, version). */
    Saida global(Duration limite, String... args);

    /** Roda {@code adb -s serial [args]}. */
    Saida noAparelho(String serial, Duration limite, List<String> args);

    /**
     * Roda um comando no shell do aparelho, mandando o texto pelo stdin de {@code sh -s}. Assim aspas,
     * acentos e {@code $} chegam intactos, sem passar pela linha de comando do Windows.
     */
    Saida shell(String serial, Duration limite, String comando);
}
