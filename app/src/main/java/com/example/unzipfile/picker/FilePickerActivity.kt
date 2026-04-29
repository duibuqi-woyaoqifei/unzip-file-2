package com.example.unzipfile.picker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unzipfile.R
import com.example.unzipfile.databinding.ActivityFilePickerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FilePickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFilePickerBinding
    private lateinit var adapter: FilePickerAdapter
    private lateinit var currentDir: File
    private var isMultiSelect = true
    private var selectType = SELECT_TYPE_FILE // 0: file, 1: folder, 2: both
    private val selectedPaths = mutableSetOf<String>()
    private var allowedExtensions: List<String>? = null

    companion object {
        const val EXTRA_MULTI_SELECT = "extra_multi_select"
        const val EXTRA_SELECT_TYPE = "extra_select_type"
        const val EXTRA_SELECTED_PATHS = "extra_selected_paths"
        const val EXTRA_ALLOWED_EXTENSIONS = "extra_allowed_extensions"
        const val EXTRA_START_PATH = "extra_start_path"
        
        const val SELECT_TYPE_FILE = 0
        const val SELECT_TYPE_FOLDER = 1
        const val SELECT_TYPE_BOTH = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFilePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isMultiSelect = intent.getBooleanExtra(EXTRA_MULTI_SELECT, true)
        selectType = intent.getIntExtra(EXTRA_SELECT_TYPE, SELECT_TYPE_FILE)
        allowedExtensions = intent.getStringArrayListExtra(EXTRA_ALLOWED_EXTENSIONS)
        
        currentDir = Environment.getExternalStorageDirectory()

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        
        loadDirectory(currentDir)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        
        binding.toolbar.title = when(selectType) {
            SELECT_TYPE_FOLDER -> "选择文件夹"
            else -> "选择文件"
        }
    }

    private fun setupRecyclerView() {
        adapter = FilePickerAdapter(
            onFileClick = { item ->
                if (item.isDirectory) {
                    loadDirectory(item.file)
                }
            },
            onSelectionChanged = { item ->
                if (item.isSelected) {
                    if (!isMultiSelect) selectedPaths.clear()
                    selectedPaths.add(item.path)
                } else {
                    selectedPaths.remove(item.path)
                }
                updateConfirmButton()
            }
        )
        adapter.isMultiSelect = isMultiSelect
        adapter.selectType = selectType
        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = adapter
    }

    private fun setupListeners() {
        binding.fabConfirm.setOnClickListener {
            val paths = selectedPaths.toList()
            
            if (paths.isEmpty() && selectType == SELECT_TYPE_FOLDER) {
                // If no folder selected explicitly but we are in a folder, maybe we want current folder?
                // Actually, let's make it explicit or allow selecting the current folder.
                val resultIntent = Intent().apply {
                    putStringArrayListExtra(EXTRA_SELECTED_PATHS, arrayListOf(currentDir.absolutePath))
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else if (paths.isNotEmpty()) {
                val resultIntent = Intent().apply {
                    putStringArrayListExtra(EXTRA_SELECTED_PATHS, ArrayList(paths))
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private fun loadDirectory(dir: File) {
        currentDir = dir
        updateBreadcrumbs()
        
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val files = dir.listFiles()?.toList() ?: emptyList()
                
                // Sort: directories first, then files, both alphabetically
                val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                
                val items = sortedFiles.map { file ->
                    FileItem(
                        file = file,
                        isDirectory = file.isDirectory,
                        isSelected = selectedPaths.contains(file.absolutePath)
                    )
                }.filter { item ->
                    // Filter based on selectType and allowedExtensions
                    val matchesType = when(selectType) {
                        SELECT_TYPE_FILE -> !item.isDirectory
                        SELECT_TYPE_FOLDER -> item.isDirectory
                        else -> true
                    }
                    
                    val matchesExtension = allowedExtensions?.let { exts ->
                        if (item.isDirectory) true
                        else exts.any { ext -> item.file.name.lowercase().endsWith(ext.lowercase()) }
                    } ?: true

                    // We always show directories for navigation
                    item.isDirectory || (matchesType && matchesExtension)
                }

                withContext(Dispatchers.Main) {
                    adapter.submitList(items)
                    binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.progressBar.visibility = View.GONE
                    updateConfirmButton()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.text = "无法访问此目录: ${e.message}"
                    binding.tvEmpty.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateBreadcrumbs() {
        binding.llBreadcrumbs.removeAllViews()
        
        val path = currentDir.absolutePath
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        
        // Add "Root" or "Internal Storage"
        addBreadcrumb("存储卡", File(rootPath))
        
        if (path.startsWith(rootPath)) {
            val relativePath = path.removePrefix(rootPath).removePrefix("/")
            if (relativePath.isNotEmpty()) {
                val parts = relativePath.split("/")
                var currentPath = rootPath
                for (part in parts) {
                    currentPath += "/$part"
                    addBreadcrumb(part, File(currentPath))
                }
            }
        }
    }

    private fun addBreadcrumb(name: String, dir: File) {
        val tv = TextView(this).apply {
            text = " > $name"
            setPadding(8, 8, 8, 8)
            setTextColor(ContextCompat.getColor(context, R.color.black))
            setOnClickListener {
                loadDirectory(dir)
            }
        }
        binding.llBreadcrumbs.addView(tv)
    }

    private fun updateConfirmButton() {
        val selectedCount = selectedPaths.size
        if (selectedCount > 0) {
            binding.fabConfirm.text = "确定 ($selectedCount)"
            binding.fabConfirm.extend()
            binding.fabConfirm.show()
        } else {
            if (selectType == SELECT_TYPE_FOLDER) {
                binding.fabConfirm.text = "选择当前目录"
                binding.fabConfirm.extend()
                binding.fabConfirm.show()
            } else {
                binding.fabConfirm.hide()
            }
        }
    }

    override fun onBackPressed() {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        if (currentDir.absolutePath != rootPath) {
            currentDir.parentFile?.let { loadDirectory(it) }
        } else {
            super.onBackPressed()
        }
    }
}
