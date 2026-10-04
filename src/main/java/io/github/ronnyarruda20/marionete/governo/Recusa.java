package io.github.ronnyarruda20.marionete.governo;

/**
 * A ação não aconteceu porque uma regra do servidor não deixou. A mensagem diz qual regra e o que o
 * humano pode fazer; a IA deve repassá-la, não contorná-la.
 */
public class Recusa extends RuntimeException {

    public Recusa(String mensagem) {
        super("Recusado: " + mensagem);
    }
}
