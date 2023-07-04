package org.android.safespace.viewmodel

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.os.FileUtils
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import org.android.safespace.lib.Constants
import org.android.safespace.lib.FileItem
import org.android.safespace.lib.FolderItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption


class AppViewModel(
    private val application: Application
) : ViewModel() {

    private var internalPath: ArrayList<String> = ArrayList()
    private var filesList: ArrayList<FileItem> = ArrayList()
    private var folderList: ArrayList<FolderItem> = ArrayList()

    var moveFileFrom: String? = null
    var moveFileTo: String? = null

    fun getFilesDir(): String {
        // root folder inside app files directory will be the first folder
        return application.filesDir.absolutePath.toString() + File.separator + Constants.ROOT
    }

    fun getInternalPath(): String {
        return internalPath.joinToString(File.separator)
    }

    fun setInternalPath(dir: String) {
        if (internalPath.isEmpty() || internalPath.last() != dir) {
            internalPath.add(dir)
        }
    }

    fun setGetPreviousPath(): String {
        val lastPath = internalPath.last()
        if (internalPath.isNotEmpty()) internalPath.removeLast()
        return lastPath
    }

    fun isRootDirectory(): Boolean {
        return internalPath.isEmpty()
    }

    fun joinPath(vararg pathList: String): String {

        return pathList.joinToString(File.separator).replace("//", "/")

    }

    fun importFile(uri: Uri, internalPath: String): Int {

        try {

            // create directory if not exists
            // createDir(internalPath, "")

            var sourceFileName = ""

            val cursor = application.contentResolver.query(
                uri, null, null, null, null, null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val colIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (colIndex >= 0) {
                        sourceFileName = it.getString(colIndex)
                    }
                }
            }

            // byte array of source file
            val sourceFileStream = application.contentResolver.openInputStream(uri)

            // output stream for target file
            val targetFileStream =
                FileOutputStream(
                    File(joinPath(getFilesDir(), internalPath, sourceFileName))
                )

            if (sourceFileStream != null) {
                FileUtils.copy(sourceFileStream, targetFileStream)
            } else {
                return -1
            }

            sourceFileStream.close()
            targetFileStream.close()

        } catch (e: Exception) {
            return -1
        }

        return 1
    }

    fun createDir(internalPath: String, newDirName: String): Int {

        try {
            val dirPath = joinPath(getFilesDir(), internalPath, newDirName)

            val newDir = File(dirPath)

            if (!newDir.exists()) {
                newDir.mkdirs()
            }

        } catch (e: FileSystemException) {
            return 0
        }

        return 1

    }

    fun initRootDir(): Int {

        try {

            val newDir = File(getFilesDir())

            if (!newDir.exists()) {
                newDir.mkdirs()
            }

        } catch (e: FileSystemException) {
            return 0
        }

        return 1

    }

    fun getContents(internalPath: String): Pair<List<FileItem>, List<FolderItem>> {

        val dirPath = File(joinPath(getFilesDir(), internalPath))

        val contents = dirPath.listFiles()

        filesList.clear()
        folderList.clear()

        for (item in contents!!) {
            if (item.isDirectory) {

                val fileInsideFolder = item.listFiles()

                val fileCount = fileInsideFolder?.size ?: 0

                folderList.add(FolderItem(item.name, fileCount))

            } else {

                filesList.add(
                    FileItem(
                        item.name,
                        item.length(),
                        item.isDirectory,
                        item.lastModified()
                    )
                )
            }
        }

        // sort -> folders first -> ascending by name
        filesList.sortWith(compareByDescending<FileItem> { it.isDir }.thenBy { it.name })

        return Pair(filesList, folderList)

    }

    fun renameFile(file: FileItem, internalPath: String, newFileName: String): Int {

        try {
            val absolutePath = joinPath(getFilesDir(), internalPath, File.separator)

            val absoluteFilePathOld = File(absolutePath + file.name)

            val absoluteFilePathNew = if ("." in file.name) {
                File(absolutePath + newFileName + "." + file.name.split(".").last())
            } else {
                File(absolutePath + newFileName)
            }

            absoluteFilePathOld.renameTo(absoluteFilePathNew)

        } catch (e: Exception) {
            return 0
        }

        return 1
    }

    fun deleteFile(file: FileItem, internalPath: String): Int {
        try {
            val fileToDelete = File(joinPath(getFilesDir(), internalPath, file.name))

            if (fileToDelete.exists()) {
                if (file.isDir) {
                    deleteDirectory(fileToDelete)
                }
                fileToDelete.delete()
            }

        } catch (e: Exception) {
            return 0
        }

        return 1

    }

    fun deleteFolder(folder: FolderItem, internalPath: String): Int {
        try {
            val folderToDelete = File(joinPath(getFilesDir(), internalPath, folder.name))

            if (folderToDelete.exists()) {
                deleteDirectory(folderToDelete)
                folderToDelete.delete()
            }

        } catch (e: Exception) {
            return 0
        }

        return 1

    }

    private fun deleteDirectory(fileToDelete: File): Int {
        try {
            val dirContents = fileToDelete.listFiles()
            for (file in dirContents!!) {
                if (file.isDirectory) {
                    deleteDirectory(File(file.absolutePath))
                } else {
                    file.delete()
                }
            }
            fileToDelete.delete()

        } catch (e: Exception) {
            return -1
        }
        return 1
    }

    fun moveFile(): Int {

        try {

            Files.move(
                Paths.get(moveFileFrom),
                Paths.get(moveFileTo),
                StandardCopyOption.REPLACE_EXISTING
            )

        } catch (e: Exception) {
            return -1
        } finally {

            moveFileFrom = null
            moveFileTo = null
        }
        return 1
    }

    fun copyFile(): Int {

        try {

            // byte array of source file
            val sourceFileStream = FileInputStream(moveFileFrom)

            // output stream for target file
            val targetFileStream = FileOutputStream(moveFileTo)

            FileUtils.copy(sourceFileStream, targetFileStream)

            sourceFileStream.close()
            targetFileStream.close()

        } catch (e: Exception) {
            return -1
        } finally {

            moveFileFrom = null
            moveFileTo = null
        }
        return 1
    }

    fun createTextNote(noteName: String): String {

        val filePath = joinPath(getFilesDir(), getInternalPath(), noteName)

        val newTextNote = File(filePath)
        val result = newTextNote.createNewFile()

        return if (result) {
            filePath
        } else {
            Constants.FILE_EXIST
        }

    }

    fun exportItems(
        selectedItems: ArrayList<FileItem>,
        selectedPath: String
    ) {

        for (item in selectedItems) {
            // start export
            exportFilesToDownloadsFolder(File(joinPath(getFilesDir(), selectedPath, item.name)).absolutePath, item.name)
        }

    }

//        TODO: "Fix this"
    private fun exportFilesToDownloadsFolder(url: String, fileName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, ".*")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = application.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            URL(url).openStream().use { input ->
                resolver.openOutputStream(uri).use { output ->
                    input.copyTo(output!!, DEFAULT_BUFFER_SIZE)
                }
            }
        }
    }

    // DO NOT REMOVE the following method
    @Suppress("unused")
    private fun recursiveDirectoryRead(
        path: String,
        _filesArray: ArrayList<String>
    ): ArrayList<String> {

        var filesArray = _filesArray

        val directoryContents = File(path).listFiles()

        for (item in directoryContents!!) {
            val filePath = joinPath(path, item.name)
            if (item.isDirectory) {
                filesArray = recursiveDirectoryRead(filePath, filesArray)
            } else {
                filesArray.add(filePath)
            }
        }

        return filesArray

    }

    fun isPreviousRootDirectory(): Boolean {
        if (this.internalPath.size == 1) {
            return true
        }
        return false
    }

}