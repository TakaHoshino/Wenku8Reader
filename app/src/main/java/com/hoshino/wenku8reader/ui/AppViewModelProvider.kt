package com.hoshino.wenku8reader.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hoshino.wenku8reader.Wenku8Application
import com.hoshino.wenku8reader.ui.author.AuthorBooksViewModel
import com.hoshino.wenku8reader.ui.bookcase.BookcaseViewModel
import com.hoshino.wenku8reader.ui.detail.DetailViewModel
import com.hoshino.wenku8reader.ui.downloads.DownloadsViewModel
import com.hoshino.wenku8reader.ui.explore.ExploreViewModel
import com.hoshino.wenku8reader.ui.explore.TagBooksViewModel
import com.hoshino.wenku8reader.ui.reader.ReaderViewModel
import com.hoshino.wenku8reader.ui.settings.SettingsViewModel
import com.hoshino.wenku8reader.ui.stats.ReadingStatsViewModel
import com.hoshino.wenku8reader.ui.toc.TocViewModel

/**
 * Central factory wiring every ViewModel to the app container. Screen-scoped
 * ViewModels receive their nav arguments via [createSavedStateHandle].
 */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer { ExploreViewModel(application().container.repository) }
        initializer {
            SettingsViewModel(
                application().container.readerSettings,
                application().container.client,
                application().container.readingProgressStore,
                application().container.storage,
                application().container.updateCenter,
            )
        }
        initializer {
            BookcaseViewModel(
                application().container.libraryStore,
                application().container.readingProgressStore,
                application().container.shelfStore,
                application().container.readerSettings,
                application().container.preferences,
            )
        }
        initializer {
            DetailViewModel(
                createSavedStateHandle(),
                application().container.repository,
                application().container.downloadEngine,
                application().container.libraryStore,
                application().container.readingProgressStore,
            )
        }
        initializer { DownloadsViewModel(application().container.downloadEngine) }
        initializer {
            TagBooksViewModel(
                createSavedStateHandle(),
                application().container.repository,
            )
        }
        initializer {
            ReaderViewModel(
                createSavedStateHandle(),
                application().container.repository,
                application().container.readingProgressStore,
                application().container.readerSettings,
                application().container.readingStats,
            )
        }
        initializer { ReadingStatsViewModel(application().container.readingStats) }
        initializer {
            AuthorBooksViewModel(
                createSavedStateHandle(),
                application().container.repository,
            )
        }
        initializer {
            TocViewModel(
                createSavedStateHandle(),
                application().container.repository,
                application().container.readingProgressStore,
            )
        }
    }

    private fun CreationExtras.application(): Wenku8Application =
        this[APPLICATION_KEY] as Wenku8Application
}
