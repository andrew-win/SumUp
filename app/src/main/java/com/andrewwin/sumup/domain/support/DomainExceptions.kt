package com.andrewwin.sumup.domain.support

class NoActiveModelException : Exception()

class UnsupportedStrategyException(message: String? = null) : Exception(message)

open class AiServiceException(message: String, val code: Int) : Exception(message)

class AiRateLimitException(message: String) : AiServiceException(message, 429)

class AiProviderUnavailableException(message: String, code: Int) : AiServiceException(message, code)

class LocalModelMissingException : Exception()

class AllAiModelsFailedException(cause: Throwable? = null) : Exception(cause?.localizedMessage, cause)

class InvalidAiResponseException : Exception()








