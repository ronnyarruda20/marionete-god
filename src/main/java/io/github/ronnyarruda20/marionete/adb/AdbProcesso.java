package io.github.ronnyarruda20.marionete.adb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Implementação real: um processo adb por chamada. */
public class AdbProcesso implements Adb {

    private final String executavel;

    public AdbProcesso(String executavel) {
        this.executavel = executavel;
    }

    @Override
    public Saida global(Duration limite, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(executavel);
        cmd.addAll(Arrays.asList(args));
        return rodar(cmd, null, limite);
    }

    @Override
    public Saida noAparelho(String serial, Duration limite, List<String> args) {
        List<String> cmd = new ArrayList<>(List.of(executavel, "-s", serial));
        cmd.addAll(args);
        return rodar(cmd, null, limite);
    }

    @Override
    public Saida shell(String serial, Duration limite, String comando) {
        List<String> cmd = List.of(executavel, "-s", serial, "shell", "sh", "-s");
        return rodar(cmd, comando + "\nexit\n", limite);
    }

    private Saida rodar(List<String> cmd, String entrada, Duration limite) {
        Process processo;
        try {
            processo = new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            throw new AdbException("Não consegui executar o adb em '" + executavel + "': " + e.getMessage()
                    + ". Defina MARIONETE_ADB com o caminho completo do adb.");
        }
        CompletableFuture<byte[]> saida = ler(processo.getInputStream());
        CompletableFuture<byte[]> erro = ler(processo.getErrorStream());
        try (OutputStream in = processo.getOutputStream()) {
            if (entrada != null) {
                in.write(entrada.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignorado) {
            // o processo pode ter saído antes de ler a entrada; o código de saída conta a história
        }
        try {
            if (!processo.waitFor(limite.toMillis(), TimeUnit.MILLISECONDS)) {
                processo.destroyForcibly();
                throw new AdbException("O aparelho não respondeu em " + limite.toSeconds() + " s ("
                        + String.join(" ", cmd.subList(1, cmd.size())) + ").");
            }
            return new Saida(processo.exitValue(), saida.join(),
                    new String(erro.join(), StandardCharsets.UTF_8).replace("\r", "").trim());
        } catch (InterruptedException e) {
            processo.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new AdbException("Interrompido enquanto esperava o adb.");
        }
    }

    private static CompletableFuture<byte[]> ler(InputStream fluxo) {
        return CompletableFuture.supplyAsync(() -> {
            try (fluxo; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                fluxo.transferTo(buffer);
                return buffer.toByteArray();
            } catch (IOException e) {
                return new byte[0];
            }
        });
    }
}
