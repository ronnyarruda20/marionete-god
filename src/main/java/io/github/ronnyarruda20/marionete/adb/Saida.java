package io.github.ronnyarruda20.marionete.adb;

import java.nio.charset.StandardCharsets;

/** O que um comando adb devolveu. */
public record Saida(int codigo, byte[] bytes, String erro) {

    public static Saida ok(String texto) {
        return new Saida(0, texto.getBytes(StandardCharsets.UTF_8), "");
    }

    public boolean ok() {
        return codigo == 0;
    }

    /** Saída como texto, sem os \r que o adb do Windows às vezes acrescenta. */
    public String texto() {
        return new String(bytes, StandardCharsets.UTF_8).replace("\r", "");
    }
}
