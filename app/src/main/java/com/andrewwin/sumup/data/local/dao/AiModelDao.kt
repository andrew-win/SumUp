package com.andrewwin.sumup.data.local.dao

import androidx.room.*
import com.andrewwin.sumup.data.local.entities.AiModelConfig
import com.andrewwin.sumup.data.local.entities.AiModelType
import kotlinx.coroutines.flow.Flow

@Dao
interface AiModelDao {
    @Query(
        """
        SELECT * FROM ai_model_configs
        ORDER BY
            type ASC,
            sortOrder ASC,
            isEnabled DESC,
            id ASC
        """
    )
    fun getAllConfigs(): Flow<List<AiModelConfig>>

    @Query(
        """
        SELECT * FROM ai_model_configs
        WHERE type = :type
        ORDER BY
            sortOrder ASC,
            id ASC
        """
    )
    fun getConfigsByType(type: AiModelType): Flow<List<AiModelConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: AiModelConfig)

    @Update
    suspend fun updateConfig(config: AiModelConfig)

    @Delete
    suspend fun deleteConfig(config: AiModelConfig)

    @Query(
        """
        SELECT * FROM ai_model_configs
        WHERE isEnabled = 1
        ORDER BY
            type ASC,
            sortOrder ASC,
            id ASC
        """
    )
    suspend fun getEnabledConfigs(): List<AiModelConfig>

    @Query(
        """
        SELECT * FROM ai_model_configs
        WHERE isEnabled = 1 AND type = :type
        ORDER BY
            sortOrder ASC,
            id ASC
        """
    )
    suspend fun getEnabledConfigsByType(type: AiModelType): List<AiModelConfig>

    @Query(
        """
        SELECT * FROM ai_model_configs
        WHERE isEnabled = 1 AND type = :type
        ORDER BY
            sortOrder ASC,
            id ASC
        LIMIT 1
        """
    )
    suspend fun getActiveConfigByType(type: AiModelType): AiModelConfig?

    @Query(
        """
        SELECT * FROM ai_model_configs
        WHERE isEnabled = 1
        ORDER BY
            type ASC,
            sortOrder ASC,
            id ASC
        LIMIT 1
        """
    )
    suspend fun getActiveConfig(): AiModelConfig?
}






