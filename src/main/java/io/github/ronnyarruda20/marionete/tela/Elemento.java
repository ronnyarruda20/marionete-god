package io.github.ronnyarruda20.marionete.tela;

import java.util.ArrayList;
import java.util.List;

/**
 * Um elemento útil da tela, com número de referência para {@code tocar}.
 *
 * @param ref    número curto, válido até a próxima leitura da tela
 * @param tipo   botão, campo, texto, imagem, lista, chave, caixa, opção, web, item ou área
 * @param rotulo texto visível ou descrição de acessibilidade (para botões sem texto, o texto dos filhos)
 * @param id     resource-id sem o prefixo do pacote, quando houver
 */
public record Elemento(
        int ref,
        String tipo,
        String rotulo,
        String id,
        int x1, int y1, int x2, int y2,
        boolean clicavel,
        boolean editavel,
        boolean rolavel,
        boolean marcavel,
        boolean marcado,
        boolean habilitado,
        boolean senha,
        boolean focado) {

    public int centroX() {
        return (x1 + x2) / 2;
    }

    public int centroY() {
        return (y1 + y2) / 2;
    }

    /** Uma linha curta: {@code [7] botão "Entrar" #login (540,1820) toca}. */
    public String linha() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(ref).append("] ").append(tipo);
        if (!rotulo.isEmpty()) {
            sb.append(" \"").append(rotulo).append('"');
        }
        if (!id.isEmpty()) {
            sb.append(" #").append(id);
        }
        sb.append(" (").append(centroX()).append(',').append(centroY()).append(')');
        List<String> flags = new ArrayList<>();
        if (clicavel) flags.add("toca");
        if (editavel) flags.add("digita");
        if (rolavel) flags.add("rola");
        if (marcavel) flags.add(marcado ? "marcado" : "desmarcado");
        if (!habilitado) flags.add("desabilitado");
        if (senha) flags.add("senha");
        if (focado) flags.add("foco");
        if (!flags.isEmpty()) {
            sb.append(' ').append(String.join(" ", flags));
        }
        return sb.toString();
    }
}
