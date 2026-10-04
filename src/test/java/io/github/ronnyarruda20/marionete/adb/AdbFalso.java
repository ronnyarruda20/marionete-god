package io.github.ronnyarruda20.marionete.adb;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * adb de mentira para testes: responde por prefixo do comando e guarda tudo o que foi pedido.
 * Comandos de shell são registrados como "shell: ...", os outros como "adb: ...".
 */
public class AdbFalso implements Adb {

    private final Map<String, Function<String, Saida>> respostas = new LinkedHashMap<>();
    private final List<String> historico = new ArrayList<>();
    private String devices = "List of devices attached\nSERIAL1\tdevice product:x model:Teste device:x transport_id:1\n";

    public AdbFalso responder(String prefixo, String texto) {
        respostas.put(prefixo, c -> Saida.ok(texto));
        return this;
    }

    public AdbFalso responder(String prefixo, Function<String, Saida> resposta) {
        respostas.put(prefixo, resposta);
        return this;
    }

    public AdbFalso responderBytes(String prefixo, byte[] bytes) {
        respostas.put(prefixo, c -> new Saida(0, bytes, ""));
        return this;
    }

    public AdbFalso devices(String saida) {
        this.devices = saida;
        return this;
    }

    public AdbFalso appNaFrente(String pacote) {
        return responder("dumpsys window",
                "  mCurrentFocus=Window{abc u0 " + pacote + "/" + pacote + ".Main}\n");
    }

    public List<String> historico() {
        return historico;
    }

    public List<String> shells() {
        return historico.stream().filter(h -> h.startsWith("shell: ")).map(h -> h.substring(7)).toList();
    }

    @Override
    public Saida global(Duration limite, String... args) {
        String cmd = String.join(" ", args);
        historico.add("adb: " + cmd);
        if (cmd.startsWith("devices")) {
            return Saida.ok(devices);
        }
        return responder(cmd);
    }

    @Override
    public Saida noAparelho(String serial, Duration limite, List<String> args) {
        String cmd = String.join(" ", args);
        historico.add("adb: " + cmd);
        return responder(cmd);
    }

    @Override
    public Saida shell(String serial, Duration limite, String comando) {
        historico.add("shell: " + comando);
        return responder(comando);
    }

    private Saida responder(String cmd) {
        // o prefixo mais longo vence, para "dumpsys window" não engolir respostas mais específicas
        return respostas.entrySet().stream()
                .filter(e -> cmd.startsWith(e.getKey()))
                .max((a, b) -> Integer.compare(a.getKey().length(), b.getKey().length()))
                .map(e -> e.getValue().apply(cmd))
                .orElse(new Saida(0, new byte[0], ""));
    }

    public static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
