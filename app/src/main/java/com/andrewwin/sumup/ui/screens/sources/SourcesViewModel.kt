package com.andrewwin.sumup.ui.screens.sources

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andrewwin.sumup.data.local.AppDatabase
import com.andrewwin.sumup.data.local.dao.GroupWithSources
import com.andrewwin.sumup.data.local.entities.Source
import com.andrewwin.sumup.data.local.entities.SourceGroup
import com.andrewwin.sumup.data.local.entities.SourceType
import com.andrewwin.sumup.data.repository.SourceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SourcesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: SourceRepository

    init {
        val dao = AppDatabase.getDatabase(application).sourceDao()
        repository = SourceRepository(dao)
    }

    val uiState: StateFlow<List<GroupWithSources>> = repository.groupsWithSources
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addGroup(name: String) {
        viewModelScope.launch {
            repository.addGroup(name)
        }
    }

    fun updateGroup(group: SourceGroup) {
        viewModelScope.launch {
            repository.updateGroup(group)
        }
    }

    fun deleteGroup(group: SourceGroup) {
        viewModelScope.launch {
            repository.deleteGroup(group)
        }
    }

    fun addSource(groupId: Long, name: String, url: String, type: SourceType) {
        viewModelScope.launch {
            repository.addSource(groupId, name, url, type)
        }
    }

    fun updateSource(source: Source) {
        viewModelScope.launch {
            repository.updateSource(source)
        }
    }

    fun deleteSource(source: Source) {
        viewModelScope.launch {
            repository.deleteSource(source)
        }
    }
    
    fun toggleGroup(group: SourceGroup, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleGroup(group, isEnabled)
        }
    }
}
