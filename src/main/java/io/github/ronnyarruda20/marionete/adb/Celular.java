package io.github.ronnyarruda20.marionete.adb;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.ronnyarruda20.marionete.tela.LeitorDeTela;
import io.github.ronnyarruda20.marionete.tela.Tela;

/** As operações no aparelho, já traduzidas para comandos adb. Sem política: isso é da Guarda. */
public class Celular {

    public record Dispositivo(String serial, String estado, String modelo) {
    }

    static final Duration RAPIDO = Duration.ofSeconds(10);
    static final Duration LENTO = Duration.ofSeconds(30);
    static final String ADB_KEYBOARD = "com.android.adbkeyboard/.AdbIME";

    private static final Pattern FOCO = Pattern.compile("u0 ([A-Za-z0-9_.]+)[/}]");
    private static final Pattern TAMANHO = Pattern.compile("(\\d+)x(\\d+)");

    private final Adb adb;
    private final String serialConfigurado;
    private final AtomicReference<Tela> ultimaTela = new AtomicReference<>();
    private volatile int[] tamanho;

    public Celular(Adb adb, String serialConfigurado) {
        this.adb = adb;
        this.serialConfigurado = serialConfigurado;
    }

    // ---------------------------------------------------------------- aparelho

    public List<Dispositivo> dispositivos() {
        Saida s = adb.global(RAPIDO, "devices", "-l");
        if (!s.ok()) {
            throw AdbException.deSaida("devices", s);
        }
        List<Dispositivo> lista = new ArrayList<>();
        for (String linha : s.texto().split("\n")) {
            linha = linha.strip();
            if (linha.isEmpty() || linha.startsWith("List of devices") || linha.startsWith("*")) {
                continue;
            }
            String[] partes = linha.split("\\s+");
            if (partes.length < 2) {
                continue;
            }
            String modelo = "";
            for (String p : partes) {
                if (p.startsWith("model:")) {
                    modelo = p.substring("model:".length());
                }
            }
            lista.add(new Dispositivo(partes[0], partes[1], modelo));
        }
        return lista;
    }

    /** O serial em uso: o configurado, ou o único aparelho pronto. */
    public String serial() {
        if (serialConfigurado != null) {
            return serialConfigurado;
        }
        List<Dispositivo> prontos = dispositivos().stream().filter(d -> d.estado().equals("device")).toList();
        if (prontos.isEmpty()) {
            throw new AdbException("Nenhum aparelho pronto. " + AdbException.dica("no devices"));
        }
        if (prontos.size() > 1) {
            throw new AdbException("Há " + prontos.size() + " aparelhos prontos ("
                    + String.join(", ", prontos.stream().map(Dispositivo::serial).toList())
                    + "). Defina MARIONETE_SERIAL com um deles.");
        }
        return prontos.get(0).serial();
    }

    /** A lista do {@code adb devices} mente por cache; isto pergunta ao aparelho de verdade. */
    public boolean responde() {
        try {
            Saida s = adb.shell(serial(), RAPIDO, "echo ok");
            return s.ok() && s.texto().strip().equals("ok");
        } catch (AdbException e) {
            return false;
        }
    }

    public String shell(String comando, Duration limite) {
        String serial = serial();
        Saida s = adb.shell(serial, limite, comando);
        if (!s.ok()) {
            throw AdbException.deSaida(primeiraPalavra(comando), s);
        }
        String texto = s.texto();
        if (texto.contains("SecurityException") && texto.contains("INJECT_EVENTS")) {
            throw new AdbException("O aparelho recusou o toque. Dica: " + AdbException.dica("inject_events"));
        }
        return texto;
    }

    public String appEmPrimeiroPlano() {
        String saida = shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'", RAPIDO);
        Matcher m = FOCO.matcher(saida);
        return m.find() ? m.group(1) : "desconhecido";
    }

    public int[] tamanhoDaTela() {
        int[] t = tamanho;
        if (t == null) {
            String saida = shell("wm size", RAPIDO);
            // "Override size" vem depois de "Physical size" quando existe, e é o que vale
            Matcher m = TAMANHO.matcher(saida);
            int[] achado = null;
            while (m.find()) {
                achado = new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
            }
            if (achado == null) {
                throw new AdbException("Não consegui ler o tamanho da tela: " + saida.strip());
            }
            tamanho = t = achado;
        }
        return t;
    }

    // ---------------------------------------------------------------- leitura

    public Tela lerTela() {
        Saida s = adb.noAparelho(serial(), LENTO, List.of("exec-out", "uiautomator", "dump", "/dev/tty"));
        String texto = s.texto();
        if (!texto.contains("</hierarchy>")) {
            // alguns aparelhos não escrevem em /dev/tty; cai para o arquivo
            texto = shell("uiautomator dump /sdcard/.marionete.xml >/dev/null && cat /sdcard/.marionete.xml"
                    + " && rm -f /sdcard/.marionete.xml", LENTO);
        }
        Tela tela = new Tela(appEmPrimeiroPlano(), LeitorDeTela.ler(texto));
        ultimaTela.set(tela);
        return tela;
    }

    /** A última tela lida, para tocar por número sem ler de novo. */
    public Tela ultimaTela() {
        return ultimaTela.get();
    }

    public byte[] capturarTela() {
        Saida s = adb.noAparelho(serial(), LENTO, List.of("exec-out", "screencap", "-p"));
        if (!s.ok() || s.bytes().length < 8) {
            throw AdbException.deSaida("screencap", s);
        }
        return s.bytes();
    }

    // ---------------------------------------------------------------- ação

    public void tocar(int x, int y) {
        shell("input tap " + x + " " + y, RAPIDO);
        invalidar();
    }

    public void tocarLongo(int x, int y, int ms) {
        shell("input swipe " + x + " " + y + " " + x + " " + y + " " + ms, RAPIDO);
        invalidar();
    }

    public void deslizar(int x1, int y1, int x2, int y2, int ms) {
        shell("input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + ms, RAPIDO);
        invalidar();
    }

    public void tecla(String keycode) {
        shell("input keyevent " + keycode, RAPIDO);
        invalidar();
    }

    /**
     * Digita no campo com foco. ASCII vai por {@code input text}; acento e emoji precisam do ADBKeyboard
     * instalado, porque o {@code input text} do Android não sabe digitá-los.
     */
    public String digitar(String texto) {
        if (texto.isEmpty()) {
            return "nada a digitar";
        }
        if (ehAsciiImprimivel(texto)) {
            shell("input text " + aspas(texto.replace(" ", "%s")), RAPIDO);
            invalidar();
            return "digitado por input text";
        }
        if (!adbKeyboardInstalado()) {
            throw new AdbException("O texto tem acento ou emoji, e o 'input text' do Android só digita ASCII. "
                    + "Instale o ADBKeyboard (github.com/senzhk/ADBKeyBoard) no aparelho para digitar em português, "
                    + "ou mande o texto sem acentos.");
        }
        String original = shell("settings get secure default_input_method", RAPIDO).strip();
        String b64 = Base64.getEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
        try {
            shell("ime enable " + ADB_KEYBOARD + " >/dev/null; ime set " + ADB_KEYBOARD + " >/dev/null; sleep 0.3; "
                    + "am broadcast -a ADB_INPUT_B64 --es msg " + b64 + " >/dev/null", RAPIDO);
        } finally {
            if (!original.isEmpty() && !original.equals("null") && !original.equals(ADB_KEYBOARD)) {
                shell("ime set " + aspas(original) + " >/dev/null", RAPIDO);
            }
        }
        invalidar();
        return "digitado pelo ADBKeyboard";
    }

    public boolean adbKeyboardInstalado() {
        return shell("pm list packages com.android.adbkeyboard", RAPIDO).contains("com.android.adbkeyboard");
    }

    public void abrirApp(String pacote) {
        String saida = shell("monkey -p " + aspas(pacote) + " -c android.intent.category.LAUNCHER 1", LENTO);
        if (saida.contains("No activities found") || saida.contains("monkey aborted")) {
            throw new AdbException("Não achei tela inicial para o pacote '" + pacote
                    + "'. Confira o nome com listar_apps.");
        }
        invalidar();
    }

    public void abrirUrl(String url) {
        String saida = shell("am start -a android.intent.action.VIEW -d " + aspas(url), LENTO);
        if (saida.contains("Error")) {
            throw new AdbException("O aparelho não abriu a URL: " + saida.strip());
        }
        invalidar();
    }

    public void fecharApp(String pacote) {
        shell("am force-stop " + aspas(pacote), RAPIDO);
        invalidar();
    }

    public String desinstalar(String pacote) {
        return shell("pm uninstall " + aspas(pacote), LENTO).strip();
    }

    public List<String> apps(boolean incluirSistema) {
        String saida = shell(incluirSistema ? "pm list packages" : "pm list packages -3", RAPIDO);
        return saida.lines()
                .map(String::strip)
                .filter(l -> l.startsWith("package:"))
                .map(l -> l.substring("package:".length()))
                .sorted()
                .toList();
    }

    // ---------------------------------------------------------------- utilidades

    private void invalidar() {
        ultimaTela.set(null);
    }

    static boolean ehAsciiImprimivel(String texto) {
        return texto.chars().allMatch(c -> c >= 0x20 && c < 0x7f);
    }

    /** Aspas simples do sh, com escape da própria aspa. */
    public static String aspas(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String primeiraPalavra(String comando) {
        String c = comando.strip();
        int i = c.indexOf(' ');
        return i < 0 ? c : c.substring(0, i);
    }
}
