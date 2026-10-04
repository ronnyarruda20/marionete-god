package io.github.ronnyarruda20.marionete.adb;

/** Falha ao falar com o aparelho, já com uma dica do que fazer. */
public class AdbException extends RuntimeException {

    public AdbException(String mensagem) {
        super(mensagem);
    }

    /** Traduz os erros mais comuns do adb em algo acionável. */
    public static AdbException deSaida(String comando, Saida saida) {
        String bruto = (saida.erro() + " " + saida.texto()).trim();
        String dica = dica(bruto);
        String resumo = bruto.length() > 300 ? bruto.substring(0, 300) + "…" : bruto;
        return new AdbException("adb falhou em '" + comando + "' (código " + saida.codigo() + "): " + resumo
                + (dica.isEmpty() ? "" : " Dica: " + dica));
    }

    static String dica(String erro) {
        String e = erro.toLowerCase();
        if (e.contains("inject_events")) {
            return "o aparelho bloqueou toque e digitação. Em aparelhos Xiaomi, ligue 'Depuração USB (Configurações "
                    + "de segurança)' nas Opções do desenvolvedor e autorize o computador de novo.";
        }
        if (e.contains("unauthorized")) {
            return "o aparelho não autorizou este computador. Desbloqueie a tela e aceite o pedido de depuração.";
        }
        if (e.contains("offline")) {
            return "o aparelho está offline para o adb. Se for USB, tente a depuração por Wi-Fi (adb connect ip:porta).";
        }
        if (e.contains("no devices") || e.contains("not found")) {
            return "nenhum aparelho conectado. Confira o cabo ou rode adb connect ip:porta.";
        }
        if (e.contains("more than one device")) {
            return "há mais de um aparelho. Defina MARIONETE_SERIAL com o serial de um deles.";
        }
        return "";
    }
}
