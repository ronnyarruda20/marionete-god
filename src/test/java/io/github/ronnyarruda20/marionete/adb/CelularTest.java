package io.github.ronnyarruda20.marionete.adb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CelularTest {

    AdbFalso adb = new AdbFalso();
    Celular celular = new Celular(adb, null);

    @Test
    void usaOUnicoAparelhoProntoEIgnoraOffline() {
        adb.devices("List of devices attached\nA1\toffline\nB2\tdevice product:fire model:23053RN02L\n");

        assertThat(celular.serial()).isEqualTo("B2");
        assertThat(celular.dispositivos()).extracting(Celular.Dispositivo::modelo).containsExactly("", "23053RN02L");
    }

    @Test
    void doisAparelhosProntosPedemOSerial() {
        adb.devices("List of devices attached\nA1\tdevice\nB2\tdevice\n");

        assertThatThrownBy(celular::serial).hasMessageContaining("MARIONETE_SERIAL").hasMessageContaining("A1, B2");
    }

    @Test
    void leOAppNaFrenteMesmoComATelaDeBloqueio() {
        adb.appNaFrente("com.android.chrome");
        assertThat(celular.appEmPrimeiroPlano()).isEqualTo("com.android.chrome");

        adb.responder("dumpsys window", "  mCurrentFocus=Window{1a2b u0 NotificationShade}\n");
        assertThat(celular.appEmPrimeiroPlano()).isEqualTo("NotificationShade");
    }

    @Test
    void digitaAsciiComEspacoEAspasSemQuebrarOShell() {
        celular.digitar("it's a test");

        assertThat(adb.shells()).containsExactly("input text 'it'\\''s%sa%stest'");
    }

    @Test
    void acentoSemAdbKeyboardExplicaComoResolver() {
        adb.responder("pm list packages com.android.adbkeyboard", "");

        assertThatThrownBy(() -> celular.digitar("ação")).hasMessageContaining("ADBKeyboard");
    }

    @Test
    void acentoComAdbKeyboardTrocaOTecladoEDevolveOOriginal() {
        adb.responder("pm list packages com.android.adbkeyboard", "package:com.android.adbkeyboard\n")
                .responder("settings get secure default_input_method", "com.google.android.inputmethod.latin/.LatinIME\n");

        celular.digitar("ação");

        assertThat(adb.shells()).anyMatch(c -> c.contains("ime set " + Celular.ADB_KEYBOARD)
                && c.contains("ADB_INPUT_B64 --es msg YcOnw6Nv"));
        assertThat(adb.shells().get(adb.shells().size() - 1))
                .isEqualTo("ime set 'com.google.android.inputmethod.latin/.LatinIME' >/dev/null");
    }

    @Test
    void toqueRecusadoPelaXiaomiViraDicaDoToggle() {
        adb.responder("input tap", "java.lang.SecurityException: Injecting input events requires INJECT_EVENTS\n");

        assertThatThrownBy(() -> celular.tocar(1, 2)).hasMessageContaining("Configurações de segurança");
    }

    @Test
    void appSemTelaInicialDizParaConferirONome() {
        adb.responder("monkey", "** No activities found to run, monkey aborted.\n");

        assertThatThrownBy(() -> celular.abrirApp("com.nao.existe")).hasMessageContaining("listar_apps");
    }
}
