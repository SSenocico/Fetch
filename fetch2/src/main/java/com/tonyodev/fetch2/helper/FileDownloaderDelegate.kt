package com.tonyodev.fetch2.helper

import android.os.Handler
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2.database.DownloadInfo
import com.tonyodev.fetch2.downloader.FileDownloader
import com.tonyodev.fetch2.util.defaultNoError
import com.tonyodev.fetch2.util.deleteRequestTempFiles
import java.io.File


class FileDownloaderDelegate(private val downloadInfoUpdater: DownloadInfoUpdater,
                             private val uiHandler: Handler,
                             private val fetchListener: FetchListener,
                             private val logger: Logger,
                             private val retryOnNetworkGain: Boolean,
                             private val requestOptions: Set<RequestOptions>,
                             private val downloader: Downloader,
                             private val fileTempDir: String) : FileDownloader.Delegate {

    override fun onStarted(download: Download, etaInMilliseconds: Long, downloadedBytesPerSecond: Long) {
        val downloadInfo = download as DownloadInfo
        downloadInfo.status = Status.DOWNLOADING
        try {
            downloadInfoUpdater.update(downloadInfo)
            uiHandler.post {
                fetchListener.onProgress(downloadInfo, etaInMilliseconds, downloadedBytesPerSecond)
            }
        } catch (e: Exception) {
            logger.e("DownloadManagerDelegate", e)
        }
    }

    private val progressRunnable = object : DownloadReportingRunnable() {
        override fun run() {
            fetchListener.onProgress(download, etaInMilliSeconds, downloadedBytesPerSecond)
        }
    }

    override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
        try {
            progressRunnable.download = download
            progressRunnable.etaInMilliSeconds = etaInMilliSeconds
            progressRunnable.downloadedBytesPerSecond = downloadedBytesPerSecond
            uiHandler.post(progressRunnable)
        } catch (e: Exception) {
            logger.e("DownloadManagerDelegate", e)
        }
    }

    override fun onError(download: Download) {
        val downloadInfo = download as DownloadInfo
        try {
            if (retryOnNetworkGain && downloadInfo.error == Error.NO_NETWORK_CONNECTION) {
                downloadInfo.status = Status.QUEUED
                downloadInfo.error = defaultNoError
                downloadInfoUpdater.update(downloadInfo)
                uiHandler.post {
                    fetchListener.onQueued(downloadInfo)
                }
            } else {
                downloadInfo.status = Status.FAILED
                when {
                    requestOptions.contains(RequestOptions.AUTO_REMOVE_ON_FAILED) -> {
                        deleteDownloadInfo(downloadInfo)
                    }
                    requestOptions.contains(RequestOptions.AUTO_REMOVE_ON_FAILED_DELETE_FILE) -> {
                        deleteDownloadInfo(downloadInfo, true)
                    }
                    else -> {
                        downloadInfoUpdater.update(downloadInfo)
                    }
                }
                uiHandler.post {
                    fetchListener.onError(downloadInfo)
                }
            }
        } catch (e: Exception) {
            logger.e("DownloadManagerDelegate", e)
        }
    }

    override fun onComplete(download: Download) {
        val downloadInfo = download as DownloadInfo
        downloadInfo.status = Status.COMPLETED
        try {
            when {
                requestOptions.contains(RequestOptions.AUTO_REMOVE_ON_COMPLETED) -> {
                    deleteDownloadInfo(downloadInfo)
                }
                requestOptions.contains(RequestOptions.AUTO_REMOVE_ON_COMPLETED_DELETE_FILE) -> {
                    deleteDownloadInfo(downloadInfo, true)
                }
                else -> {
                    downloadInfoUpdater.update(downloadInfo)
                }
            }
            uiHandler.post {
                fetchListener.onCompleted(downloadInfo)
            }
        } catch (e: Exception) {
            logger.e("DownloadManagerDelegate", e)
        }
    }

    override fun saveDownloadProgress(download: Download) {
        try {
            val downloadInfo = download as DownloadInfo
            downloadInfo.status = Status.DOWNLOADING
            downloadInfoUpdater.updateFileBytesInfoAndStatusOnly(downloadInfo)
        } catch (e: Exception) {
            logger.e("DownloadManagerDelegate", e)
        }
    }

    private fun deleteDownloadInfo(downloadInfo: DownloadInfo, deleteFile: Boolean = false) {
        val file = File(downloadInfo.file)
        downloadInfoUpdater.deleteDownload(downloadInfo)
        if (deleteFile && file.exists()) {
            file.delete()
            deleteRequestTempFiles(fileTempDir, downloader, downloadInfo)
        }
    }

}