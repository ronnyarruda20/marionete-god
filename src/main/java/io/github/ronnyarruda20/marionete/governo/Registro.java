package io.github.ronnyarruda20.marionete.governo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uma linha JSON por chamada de ferramenta, em {@code registro.jsonl}: quem, quando, o quê, e como
 * terminou. Vale nos dois modos. O texto digitado entra só como tamanho, salvo configuração contrária.
 */
public class Registro {

    private final Path arquivo;
    private final Clock relogio;
    private final long pid = ProcessHandle.current().pid();

    public Registro(Path pasta, Clock relogio) {
        this.arquivo = pasta.resolve("registro.jsonl");
        this.relogio = relogio;
    }

    public Path arquivo() {
        return arquivo;
    }

    public synchronized void anotar(String modo, String ferramenta, Map<String, Object> parametros,
            String resultado, String detalhe, long ms) {
        Map<String, Object> linha = new LinkedHashMap<>();
        linha.put("quando", Instant.now(relogio).toString());
        linha.put("sessao", pid);
        linha.put("modo", modo);
        linha.put("ferramenta", ferramenta);
        linha.put("parametros", parametros);
        linha.put("resultado", resultado);
        if (detalhe != null && !detalhe.isBlank()) {
            linha.put("detalhe", detalhe.length() > 500 ? detalhe.substring(0, 500) + "…" : detalhe);
        }
        linha.put("ms", ms);
        try {
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, json(linha) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // registro que falha não pode derrubar a ação; vai para o log do servidor
            System.err.println("marionete: não consegui escrever o registro em " + arquivo + ": " + e.getMessage());
        }
    }

    static String json(Object valor) {
        if (valor == null) {
            return "null";
        }
        if (valor instanceof Number || valor instanceof Boolean) {
            return valor.toString();
        }
        if (valor instanceof Map<?, ?> mapa) {
            StringBuilder sb = new StringBuilder("{");
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                sb.append(json(String.valueOf(e.getKey()))).append(':').append(json(e.getValue()));
            }
            return sb.append('}').toString();
        }
        String s = valor.toString();
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
