package zechs.drive.stream.domain.common

/**
 * Representação de erro de domínio com mensagem segura para exibição ao usuário
 * e causa técnica encapsulada (destinada apenas para logs internos seguros).
 */
sealed class AppError(
    val userMessage: String,
    val technicalCause: Throwable? = null
) {
    val displayMessage: String get() = userMessage

    class Network(
        message: String = "Falha de conexão com a rede. Verifique sua internet.",
        cause: Throwable? = null
    ) : AppError(message, cause)

    class Authentication(
        message: String = "Sessão expirada ou não autenticada. Faça login novamente.",
        cause: Throwable? = null
    ) : AppError(message, cause)

    class NotFound(
        message: String = "Arquivo ou metadado não encontrado.",
        cause: Throwable? = null
    ) : AppError(message, cause)

    class UnsupportedCodec(
        message: String = "Formato ou codec de mídia não suportado pelo motor atual.",
        cause: Throwable? = null
    ) : AppError(message, cause)

    class RateLimited(
        message: String = "Limite de requisições excedido. Tente novamente mais tarde.",
        cause: Throwable? = null
    ) : AppError(message, cause)

    class AccessDenied(
        message: String = "Acesso negado aos arquivos.",
        cause: Throwable? = null
    ) : AppError(message, cause)

    class Server(
        message: String = "Instabilidade nos servidores. Tente novamente.",
        cause: Throwable? = null
    ) : AppError(message, cause)

    class Empty(
        message: String = "Nenhum item encontrado.",
        cause: Throwable? = null
    ) : AppError(message, cause)

    class Unknown(
        message: String = "Ocorreu um erro inesperado. Tente novamente.",
        cause: Throwable? = null
    ) : AppError(message, cause)
}
