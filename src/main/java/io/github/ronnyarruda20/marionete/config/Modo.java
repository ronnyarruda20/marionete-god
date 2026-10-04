package io.github.ronnyarruda20.marionete.config;

/**
 * Até onde os fios alcançam. O modo vem só da configuração da máquina: nenhuma ferramenta o altera,
 * para que a IA (ou um texto que ela leia na tela) nunca consiga se promover a modo god.
 */
public enum Modo {

    /** Apps negados por padrão, confirmação humana para ação destrutiva, sem shell livre. */
    PADRAO,

    /** Tudo liberado, salvo os apps que a configuração negar explicitamente. Registro e fios continuam. */
    GOD;

    public static Modo de(String valor) {
        if (valor == null || valor.isBlank()) {
            return PADRAO;
        }
        return switch (valor.trim().toLowerCase()) {
            case "god" -> GOD;
            case "padrao", "padrão", "default" -> PADRAO;
            default -> throw new IllegalArgumentException(
                    "marionete.modo inválido: '" + valor + "'. Use 'padrao' ou 'god'.");
        };
    }
}
