package io.github.ronnyarruda20.marionete.governo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.LongPredicate;

/**
 * Uma sessão de IA por vez no aparelho. Cada cliente MCP sobe o seu próprio processo do servidor; o
 * primeiro que usar o celular fica com ele. A trava expira sozinha depois de um tempo sem uso, ou na
 * hora se o processo dono não existir mais.
 */
public class TravaDeSessao {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final Path arquivo;
    private final Duration expiraEm;
    private final Clock relogio;
    private final long meuPid;
    private final LongPredicate processoVivo;

    public TravaDeSessao(Path pasta, Duration expiraEm, Clock relogio) {
        this(pasta, expiraEm, relogio, ProcessHandle.current().pid(),
                pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }

    TravaDeSessao(Path pasta, Duration expiraEm, Clock relogio, long meuPid, LongPredicate processoVivo) {
        this.arquivo = pasta.resolve("trava.txt");
        this.expiraEm = expiraEm;
        this.relogio = relogio;
        this.meuPid = meuPid;
        this.processoVivo = processoVivo;
    }

    /** Pega a trava, ou renova se já é nossa. Recusa se outra sessão viva a usou há pouco. */
    public void pegar() {
        comArquivo(canal -> {
            Dono atual = ler(canal);
            Instant agora = Instant.now(relogio);
            if (atual != null && atual.pid() != meuPid && processoVivo.test(atual.pid())
                    && atual.ultimoUso().plus(expiraEm).isAfter(agora)) {
                long faltam = Duration.between(agora, atual.ultimoUso().plus(expiraEm)).toMinutes() + 1;
                throw new Recusa("o celular está com outra sessão de IA (processo " + atual.pid() + ", em uso desde "
                        + HORA.format(atual.desde()) + "). Ele libera sozinho em até " + faltam
                        + " min sem uso, ou quando aquela sessão fechar.");
            }
            Instant desde = (atual != null && atual.pid() == meuPid) ? atual.desde() : agora;
            escrever(canal, new Dono(meuPid, desde, agora));
            return null;
        });
    }

    /** Solta a trava se ela for nossa (ao desligar o servidor). */
    public void soltar() {
        if (!Files.exists(arquivo)) {
            return;
        }
        comArquivo(canal -> {
            Dono atual = ler(canal);
            if (atual != null && atual.pid() == meuPid) {
                canal.truncate(0);
            }
            return null;
        });
    }

    public String descrever() {
        if (!Files.exists(arquivo)) {
            return "livre";
        }
        return comArquivo(canal -> {
            Dono d = ler(canal);
            if (d == null) {
                return "livre";
            }
            if (d.pid() == meuPid) {
                return "com esta sessão desde " + HORA.format(d.desde());
            }
            boolean valida = processoVivo.test(d.pid()) && d.ultimoUso().plus(expiraEm).isAfter(Instant.now(relogio));
            return valida ? "com outra sessão (processo " + d.pid() + ") desde " + HORA.format(d.desde())
                    : "livre (a última sessão expirou)";
        });
    }

    record Dono(long pid, Instant desde, Instant ultimoUso) {
    }

    private interface Operacao<T> {
        T em(FileChannel canal) throws IOException;
    }

    private <T> T comArquivo(Operacao<T> operacao) {
        try {
            Files.createDirectories(arquivo.getParent());
            try (FileChannel canal = FileChannel.open(arquivo, StandardOpenOption.CREATE, StandardOpenOption.READ,
                    StandardOpenOption.WRITE); FileLock ignorado = canal.lock()) {
                return operacao.em(canal);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não consegui usar a trava em " + arquivo, e);
        }
    }

    private static Dono ler(FileChannel canal) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(canal.size(), 4096));
        canal.read(buffer, 0);
        String conteudo = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8).strip();
        String[] partes = conteudo.split("\\s+");
        if (partes.length != 3) {
            return null;
        }
        try {
            return new Dono(Long.parseLong(partes[0]), Instant.parse(partes[1]), Instant.parse(partes[2]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void escrever(FileChannel canal, Dono dono) throws IOException {
        byte[] bytes = (dono.pid() + " " + dono.desde() + " " + dono.ultimoUso() + "\n").getBytes(StandardCharsets.UTF_8);
        canal.truncate(0);
        canal.write(ByteBuffer.wrap(bytes), 0);
    }
}
