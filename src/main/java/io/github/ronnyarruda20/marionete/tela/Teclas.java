package io.github.ronnyarruda20.marionete.tela;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Nomes em português para as teclas que fazem sentido num agente. */
public final class Teclas {

    public static final Map<String, String> CODIGOS;

    /** Teclas que tiram a IA de onde ela está. Valem até com um app negado na frente. */
    public static final Set<String> FUGA = Set.of("voltar", "inicio", "recentes");

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("voltar", "KEYCODE_BACK");
        m.put("inicio", "KEYCODE_HOME");
        m.put("recentes", "KEYCODE_APP_SWITCH");
        m.put("enter", "KEYCODE_ENTER");
        m.put("apagar", "KEYCODE_DEL");
        m.put("tab", "KEYCODE_TAB");
        m.put("esc", "KEYCODE_ESCAPE");
        m.put("acordar", "KEYCODE_WAKEUP");
        m.put("dormir", "KEYCODE_SLEEP");
        m.put("volume_mais", "KEYCODE_VOLUME_UP");
        m.put("volume_menos", "KEYCODE_VOLUME_DOWN");
        m.put("menu", "KEYCODE_MENU");
        m.put("buscar", "KEYCODE_SEARCH");
        m.put("cima", "KEYCODE_DPAD_UP");
        m.put("baixo", "KEYCODE_DPAD_DOWN");
        m.put("esquerda", "KEYCODE_DPAD_LEFT");
        m.put("direita", "KEYCODE_DPAD_RIGHT");
        CODIGOS = Map.copyOf(m);
    }

    private Teclas() {
    }

    /** Normaliza o nome ("Início", "HOME", "inicio") para a chave do mapa. */
    public static String normalizar(String nome) {
        String n = Tela.normalizar(nome).replace(' ', '_').replace('-', '_');
        return switch (n) {
            case "home" -> "inicio";
            case "back" -> "voltar";
            case "recents", "app_switch" -> "recentes";
            case "backspace", "del", "delete" -> "apagar";
            default -> n;
        };
    }

    public static String nomesAceitos() {
        return String.join(", ", new java.util.TreeSet<>(CODIGOS.keySet()));
    }
}
