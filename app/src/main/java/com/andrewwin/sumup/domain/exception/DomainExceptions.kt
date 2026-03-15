package com.andrewwin.sumup.domain.exception

class NoActiveModelException : Exception()

class UnsupportedStrategyException : Exception()

open class AiServiceException(message: String, val code: Int) : Exception(message)

class AiRateLimitException(message: String) : AiServiceException(message, 429)

class AiProviderUnavailableException(message: String, code: Int) : AiServiceException(message, code)

class AllAiModelsFailedException : Exception()
