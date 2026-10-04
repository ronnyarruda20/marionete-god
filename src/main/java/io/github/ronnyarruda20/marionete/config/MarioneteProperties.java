package io.github.ronnyarruda20.marionete.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da máquina onde o servidor roda.
 *
 * @param modo           "padrao" ou "god"
 * @param adb            caminho do executável adb (ou só "adb", se estiver no PATH)
 * @param serial         serial do aparelho; vazio = o único conectado
 * @param pasta          onde ficam o registro, a trava entre sessões e o arquivo que corta os fios
 * @param appsNegados    pacotes que a IA não pode usar; ausente = a lista padrão do modo; "nenhum" = lista vazia
 * @param travaExpiraEm  quanto tempo sem uso até a trava de uma sessão expirar sozinha
 * @param registrarTexto se o texto digitado entra no registro (padrão: não, só o tamanho)
 */
@ConfigurationProperties("marionete")
public record MarioneteProperties(
        String modo,
        String adb,
        String serial,
        Path pasta,
        List<String> appsNegados,
        Duration travaExpiraEm,
        Boolean registrarTexto) {

    /** Apps em que toque automatizado costuma custar a conta. Só valem no modo padrão. */
    public static final List<String> NEGADOS_NO_PADRAO = List.of(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.instagram.android");

    public MarioneteProperties {
        adb = (adb == null || adb.isBlank()) ? "adb" : adb.trim();
        serial = (serial == null || serial.isBlank()) ? null : serial.trim();
        pasta = pasta == null ? Path.of(System.getProperty("user.home"), ".marionete-god") : pasta;
        travaExpiraEm = travaExpiraEm == null ? Duration.ofMinutes(5) : travaExpiraEm;
        registrarTexto = registrarTexto != null && registrarTexto;
    }

    public Modo modoEfetivo() {
        return Modo.de(modo);
    }

    public List<String> negadosEfetivos() {
        if (appsNegados == null) {
            return modoEfetivo() == Modo.GOD ? List.of() : NEGADOS_NO_PADRAO;
        }
        return appsNegados.stream()
                .map(String::trim)
                .filter(p -> !p.isEmpty() && !p.equalsIgnoreCase("nenhum"))
                .toList();
    }
}
