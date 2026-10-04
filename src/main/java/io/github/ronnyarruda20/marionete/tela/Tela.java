package io.github.ronnyarruda20.marionete.tela;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Uma leitura da tela: o app na frente e os elementos úteis. */
public record Tela(String app, List<Elemento> elementos) {

    public Optional<Elemento> porRef(int ref) {
        return elementos.stream().filter(e -> e.ref() == ref).findFirst();
    }

    /**
     * Acha um elemento pelo texto: primeiro igual (sem acento e sem caixa), depois contendo. Entre os
     * candidatos, prefere o que aceita toque.
     */
    public Optional<Elemento> porTexto(String procurado) {
        String alvo = normalizar(procurado);
        if (alvo.isEmpty()) {
            return Optional.empty();
        }
        Comparator<Elemento> clicavelPrimeiro = Comparator.comparing(e -> !e.clicavel());
        Optional<Elemento> igual = elementos.stream()
                .filter(e -> normalizar(e.rotulo()).equals(alvo) || normalizar(e.id()).equals(alvo))
                .min(clicavelPrimeiro);
        if (igual.isPresent()) {
            return igual;
        }
        return elementos.stream()
                .filter(e -> normalizar(e.rotulo()).contains(alvo))
                .min(clicavelPrimeiro);
    }

    public boolean contemTexto(String procurado) {
        String alvo = normalizar(procurado);
        return elementos.stream().anyMatch(e -> normalizar(e.rotulo()).contains(alvo));
    }

    public String formatar() {
        StringBuilder sb = new StringBuilder();
        sb.append("App na frente: ").append(app).append('\n');
        if (elementos.isEmpty()) {
            sb.append("Nenhum elemento legível. A tela pode ser um jogo, Flutter, vídeo ou estar carregando: ")
                    .append("use capturar_tela.");
            return sb.toString();
        }
        sb.append(elementos.size()).append(" elementos (número entre colchetes serve para tocar; ")
                .append("coordenadas são o centro, em pixels do aparelho):\n");
        elementos.forEach(e -> sb.append(e.linha()).append('\n'));
        return sb.toString().stripTrailing();
    }

    static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
