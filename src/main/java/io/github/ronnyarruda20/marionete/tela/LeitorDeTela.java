package io.github.ronnyarruda20.marionete.tela;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Transforma o XML do {@code uiautomator dump} numa lista curta de elementos úteis.
 *
 * <p>O XML bruto de uma tela comum tem dezenas de KB, quase tudo contêiner vazio. Aqui só entra o que
 * tem texto, descrição ou aceita alguma ação. Um botão sem texto próprio herda o texto dos filhos, e
 * esses filhos não se repetem na lista.
 */
public final class LeitorDeTela {

    private static final Pattern LIMITES = Pattern.compile("\\[(-?\\d+),(-?\\d+)]\\[(-?\\d+),(-?\\d+)]");
    private static final int ROTULO_MAX = 80;

    private LeitorDeTela() {
    }

    public static List<Elemento> ler(String saidaDoDump) {
        String xml = recortarXml(saidaDoDump);
        Element raiz;
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setExpandEntityReferences(false);
            raiz = fabrica.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("Não consegui ler a árvore da tela: " + e.getMessage(), e);
        }
        List<Elemento> saida = new ArrayList<>();
        visitar(raiz, "", saida);
        return saida;
    }

    /** O dump para /dev/tty vem com uma linha de aviso depois do XML; corta o que não é XML. */
    static String recortarXml(String saida) {
        int inicio = saida.indexOf("<?xml");
        if (inicio < 0) {
            inicio = saida.indexOf("<hierarchy");
        }
        int fim = saida.lastIndexOf("</hierarchy>");
        if (inicio < 0 || fim < 0) {
            String inicioSaida = saida.length() > 200 ? saida.substring(0, 200) : saida;
            throw new IllegalStateException("O aparelho não devolveu a árvore da tela. Saída: " + inicioSaida.trim());
        }
        return saida.substring(inicio, fim + "</hierarchy>".length());
    }

    /** @param rotuloDoPai rótulo (normalizado) do botão mais próximo acima; vazio se não houver */
    private static void visitar(Element no, String rotuloDoPai, List<Elemento> saida) {
        boolean ehNo = "node".equals(no.getTagName());
        String rotuloParaFilhos = rotuloDoPai;

        if (ehNo) {
            String classe = no.getAttribute("class");
            String texto = no.getAttribute("text").strip();
            String descricao = no.getAttribute("content-desc").strip();
            boolean clicavel = sim(no, "clickable") || sim(no, "long-clickable");
            boolean marcavel = sim(no, "checkable");
            boolean rolavel = sim(no, "scrollable");
            boolean editavel = classe.contains("EditText") || classe.contains("AutoCompleteTextView");
            int[] l = limites(no.getAttribute("bounds"));
            boolean visivel = l != null && l[2] > l[0] && l[3] > l[1];

            String rotulo = !texto.isEmpty() ? texto : descricao;
            if (rotulo.isEmpty() && (clicavel || marcavel)) {
                rotulo = String.join(" · ", textosDosFilhos(no, 3));
            }

            boolean util = clicavel || editavel || rolavel || marcavel || !rotulo.isEmpty();
            // texto puro que só repete o rótulo do botão de cima não se repete na lista
            boolean soTexto = !clicavel && !editavel && !rolavel && !marcavel;
            boolean redundante = soTexto && !rotuloDoPai.isEmpty() && rotuloDoPai.contains(Tela.normalizar(rotulo));

            if (visivel && util && !redundante) {
                saida.add(new Elemento(
                        saida.size() + 1,
                        tipo(classe, clicavel, !rotulo.isEmpty()),
                        cortar(rotulo),
                        idCurto(no.getAttribute("resource-id")),
                        l[0], l[1], l[2], l[3],
                        clicavel, editavel, rolavel, marcavel,
                        sim(no, "checked"),
                        !"false".equals(no.getAttribute("enabled")),
                        sim(no, "password"),
                        sim(no, "focused")));
            }
            if (visivel && (clicavel || marcavel) && !rotulo.isEmpty()) {
                rotuloParaFilhos = Tela.normalizar(rotulo);
            }
        }

        for (Node filho = no.getFirstChild(); filho != null; filho = filho.getNextSibling()) {
            if (filho instanceof Element e) {
                visitar(e, rotuloParaFilhos, saida);
            }
        }
    }

    private static List<String> textosDosFilhos(Element no, int maximo) {
        List<String> textos = new ArrayList<>();
        coletar(no, textos, maximo);
        return textos;
    }

    private static void coletar(Element no, List<String> textos, int maximo) {
        for (Node filho = no.getFirstChild(); filho != null && textos.size() < maximo; filho = filho.getNextSibling()) {
            if (filho instanceof Element e) {
                String t = e.getAttribute("text").strip();
                if (t.isEmpty()) {
                    t = e.getAttribute("content-desc").strip();
                }
                if (!t.isEmpty()) {
                    textos.add(t);
                }
                coletar(e, textos, maximo);
            }
        }
    }

    static String tipo(String classe, boolean clicavel, boolean temRotulo) {
        String c = classe.substring(classe.lastIndexOf('.') + 1);
        if (c.contains("EditText") || c.contains("AutoComplete")) return "campo";
        if (c.contains("CheckBox")) return "caixa";
        if (c.contains("Switch") || c.contains("ToggleButton")) return "chave";
        if (c.contains("RadioButton")) return "opção";
        if (c.contains("Button")) return "botão";
        if (c.contains("ImageView")) return clicavel ? "botão" : "imagem";
        if (c.contains("WebView")) return "web";
        if (c.contains("RecyclerView") || c.contains("ListView") || c.contains("ScrollView")
                || c.contains("ViewPager") || c.contains("GridView")) return "lista";
        if (c.contains("TextView")) return clicavel ? "item" : "texto";
        if (clicavel) return "item";
        return temRotulo ? "texto" : "área";
    }

    private static boolean sim(Element no, String atributo) {
        return "true".equals(no.getAttribute(atributo));
    }

    private static int[] limites(String bounds) {
        Matcher m = LIMITES.matcher(bounds);
        if (!m.matches()) {
            return null;
        }
        return new int[] {
                Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))};
    }

    private static String idCurto(String resourceId) {
        int i = resourceId.indexOf(":id/");
        return i >= 0 ? resourceId.substring(i + 4) : resourceId;
    }

    private static String cortar(String rotulo) {
        String umaLinha = rotulo.replaceAll("\\s+", " ").replace("\"", "'");
        return umaLinha.length() > ROTULO_MAX ? umaLinha.substring(0, ROTULO_MAX - 1) + "…" : umaLinha;
    }
}
