package com.hoshino.wenku8reader.ui.downloads

import androidx.lifecycle.ViewModel
import com.hoshino.wenku8reader.data.DownloadEngine
import com.hoshino.wenku8reader.data.DownloadJob
import kotlinx.coroutines.flow.StateFlow

class DownloadsViewModel(private val downloadEngine: DownloadEngine) : ViewModel() {

    val jobs: StateFlow<Map<Int, DownloadJob>> = downloadEngine.jobs
}
