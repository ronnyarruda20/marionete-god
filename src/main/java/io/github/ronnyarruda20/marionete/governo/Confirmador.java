package io.github.ronnyarruda20.marionete.governo;

import java.util.List;
import java.util.Map;

import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Confirmação humana pelo próprio protocolo MCP (elicitation): o servidor pausa a ferramenta e o
 * cliente mostra a pergunta ao usuário. O modelo não responde por ele.
 */
public class Confirmador {

    /** @return true só se o humano aceitou e marcou "confirmar". */
    public boolean confirmar(McpSyncRequestContext contexto, String pergunta) {
        if (contexto == null || !contexto.elicitEnabled()) {
            throw new Recusa("esta ação pede confirmação humana no modo padrão, e o cliente MCP conectado não "
                    + "sabe perguntar (falta suporte a elicitation; o Claude Code tem desde a versão 2.1.76). "
                    + "Faça a ação à mão, ou rode o servidor em modo god se o aparelho permitir.");
        }
        McpSchema.ElicitRequest pedido = McpSchema.ElicitRequest.builder()
                .message(pergunta)
                .requestedSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("confirmar", Map.of(
                                "type", "boolean",
                                "title", "Confirmar",
                                "description", "Marque para deixar a IA executar esta ação no celular.")),
                        "required", List.of("confirmar")))
                .build();
        McpSchema.ElicitResult resposta = contexto.elicit(pedido);
        return resposta != null
                && resposta.action() == McpSchema.ElicitResult.Action.ACCEPT
                && resposta.content() != null
                && Boolean.TRUE.equals(resposta.content().get("confirmar"));
    }
}
