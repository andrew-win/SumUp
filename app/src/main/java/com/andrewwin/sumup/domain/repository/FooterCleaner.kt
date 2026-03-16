package com.andrewwin.sumup.domain.repository

interface FooterCleaner {
    fun findCommonFooter(texts: List<String>): String?
    fun removeFooter(text: String, footerPattern: String?): String
}
