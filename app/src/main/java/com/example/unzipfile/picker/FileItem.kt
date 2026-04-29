package com.example.unzipfile.picker

import java.io.File

data class FileItem(
    val file: File,
    val isDirectory: Boolean,
    var isSelected: Boolean = false,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val size: Long = if (file.isFile) file.length() else 0,
    val lastModified: Long = file.lastModified()
)
