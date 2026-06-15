package com.andrewwin.sumup.data.ai.prompt

import android.content.Context
import com.andrewwin.sumup.domain.ai.prompt.PromptTemplateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetsPromptTemplateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PromptTemplateRepository {

    private val templates = mutableMapOf<String, String>()

    override fun getTemplate(path: String): String {
        return templates.getOrPut(path) {
            context.assets.open(path).bufferedReader().use { reader ->
                reader.readText().trim()
            }
        }
    }
}
