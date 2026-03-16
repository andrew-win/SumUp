package com.andrewwin.sumup.domain.repository

interface TextCleaner {
    fun clean(text: String): String
}
