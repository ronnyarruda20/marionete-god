package io.github.ronnyarruda20.marionete.governo;

/**
 * O tipo de cada ferramenta, do ponto de vista da governança.
 *
 * @param usaAparelho   precisa da trava entre sessões e respeita os fios cortados
 * @param ageNaTela     é recusada se um app negado estiver na frente, e conferida depois de executar
 * @param destrutiva    pede confirmação humana no modo padrão
 * @param soNoGod       recusada no modo padrão
 */
public enum Acao {

    CONSULTA(false, false, false, false),
    LEITURA(true, false, false, false),
    TOQUE(true, true, false, false),
    FUGA(true, false, false, false),
    ABRIR(true, false, false, false),
    FECHAR(true, false, false, false),
    DESTRUTIVA(true, false, true, false),
    SHELL(true, false, true, true);

    public final boolean usaAparelho;
    public final boolean ageNaTela;
    public final boolean destrutiva;
    public final boolean soNoGod;

    Acao(boolean usaAparelho, boolean ageNaTela, boolean destrutiva, boolean soNoGod) {
        this.usaAparelho = usaAparelho;
        this.ageNaTela = ageNaTela;
        this.destrutiva = destrutiva;
        this.soNoGod = soNoGod;
    }
}
