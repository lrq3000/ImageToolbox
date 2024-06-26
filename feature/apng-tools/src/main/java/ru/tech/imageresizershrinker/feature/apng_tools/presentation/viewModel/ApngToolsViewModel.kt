/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2024 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

@file:Suppress("FunctionName")

package ru.tech.imageresizershrinker.feature.apng_tools.presentation.viewModel

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.tech.imageresizershrinker.core.domain.dispatchers.DispatchersHolder
import ru.tech.imageresizershrinker.core.domain.image.ImageCompressor
import ru.tech.imageresizershrinker.core.domain.image.ImageGetter
import ru.tech.imageresizershrinker.core.domain.image.ShareProvider
import ru.tech.imageresizershrinker.core.domain.image.model.ImageFormat
import ru.tech.imageresizershrinker.core.domain.image.model.ImageFrames
import ru.tech.imageresizershrinker.core.domain.image.model.ImageInfo
import ru.tech.imageresizershrinker.core.domain.image.model.Quality
import ru.tech.imageresizershrinker.core.domain.saving.FileController
import ru.tech.imageresizershrinker.core.domain.saving.model.FileSaveTarget
import ru.tech.imageresizershrinker.core.domain.saving.model.ImageSaveTarget
import ru.tech.imageresizershrinker.core.domain.saving.model.SaveResult
import ru.tech.imageresizershrinker.core.domain.saving.model.SaveTarget
import ru.tech.imageresizershrinker.core.ui.utils.BaseViewModel
import ru.tech.imageresizershrinker.core.ui.utils.navigation.Screen
import ru.tech.imageresizershrinker.core.ui.utils.state.update
import ru.tech.imageresizershrinker.feature.apng_tools.domain.ApngConverter
import ru.tech.imageresizershrinker.feature.apng_tools.domain.ApngParams
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ApngToolsViewModel @Inject constructor(
    private val imageCompressor: ImageCompressor<Bitmap>,
    private val imageGetter: ImageGetter<Bitmap, ExifInterface>,
    private val fileController: FileController,
    private val apngConverter: ApngConverter,
    private val shareProvider: ShareProvider<Bitmap>,
    defaultDispatchersHolder: DispatchersHolder
) : BaseViewModel(defaultDispatchersHolder) {

    private val _type: MutableState<Screen.ApngTools.Type?> = mutableStateOf(null)
    val type by _type

    private val _isLoading: MutableState<Boolean> = mutableStateOf(false)
    val isLoading by _isLoading

    private val _isLoadingApngImages: MutableState<Boolean> = mutableStateOf(false)
    val isLoadingApngImages by _isLoadingApngImages

    private val _params: MutableState<ApngParams> = mutableStateOf(ApngParams.Default)
    val params by _params

    private val _convertedImageUris: MutableState<List<String>> = mutableStateOf(emptyList())
    val convertedImageUris by _convertedImageUris

    private val _imageFormat: MutableState<ImageFormat> = mutableStateOf(ImageFormat.Png.Lossless)
    val imageFormat by _imageFormat

    private val _imageFrames: MutableState<ImageFrames> = mutableStateOf(ImageFrames.All)
    val imageFrames by _imageFrames

    private val _done: MutableState<Int> = mutableIntStateOf(0)
    val done by _done

    private val _left: MutableState<Int> = mutableIntStateOf(-1)
    val left by _left

    private val _isSaving: MutableState<Boolean> = mutableStateOf(false)
    val isSaving: Boolean by _isSaving

    private val _jxlQuality: MutableState<Quality.Jxl> = mutableStateOf(Quality.Jxl())
    val jxlQuality by _jxlQuality

    private var apngData: ByteArray? = null

    fun setType(type: Screen.ApngTools.Type) {
        when (type) {
            is Screen.ApngTools.Type.ApngToImage -> {
                type.apngUri?.let { setApngUri(it) } ?: _type.update { null }
            }

            is Screen.ApngTools.Type.ImageToApng -> {
                _type.update { type }
            }

            is Screen.ApngTools.Type.ApngToJxl -> {
                _type.update { type }
            }
        }
    }

    fun setImageUris(uris: List<Uri>) {
        clearAll()
        _type.update {
            Screen.ApngTools.Type.ImageToApng(uris)
        }
    }

    private var collectionJob: Job? = null
    fun setApngUri(uri: Uri) {
        clearAll()
        _type.update {
            Screen.ApngTools.Type.ApngToImage(uri)
        }
        updateApngFrames(ImageFrames.All)
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch(defaultDispatcher) {
            _isLoading.update { true }
            _isLoadingApngImages.update { true }
            apngConverter.extractFramesFromApng(
                apngUri = uri.toString(),
                imageFormat = imageFormat,
                quality = params.quality
            ).onCompletion {
                _isLoading.update { false }
                _isLoadingApngImages.update { false }
            }.collect { nextUri ->
                if (isLoading) {
                    _isLoading.update { false }
                }
                _convertedImageUris.update { it + nextUri }
            }
        }
    }

    fun clearAll() {
        collectionJob?.cancel()
        collectionJob = null
        _type.update { null }
        _convertedImageUris.update { emptyList() }
        apngData = null
        savingJob?.cancel()
        savingJob = null
        updateParams(ApngParams.Default)
    }

    fun updateApngFrames(imageFrames: ImageFrames) {
        _imageFrames.update { imageFrames }
    }

    fun clearConvertedImagesSelection() = updateApngFrames(ImageFrames.ManualSelection(emptyList()))

    fun selectAllConvertedImages() = updateApngFrames(ImageFrames.All)

    private var savingJob: Job? = null

    fun saveApngTo(
        outputStream: OutputStream?,
        onComplete: (Throwable?) -> Unit
    ) {
        _isSaving.value = false
        savingJob?.cancel()
        savingJob = viewModelScope.launch {
            withContext(defaultDispatcher) {
                _isSaving.value = true
                kotlin.runCatching {
                    outputStream?.use {
                        it.write(apngData)
                    }
                }.exceptionOrNull().let(onComplete)
                _isSaving.value = false
                apngData = null
            }
        }
    }

    fun saveBitmaps(
        onApngSaveResult: (String) -> Unit,
        onResult: (List<SaveResult>, String) -> Unit
    ) {
        _isSaving.value = false
        savingJob?.cancel()
        savingJob = viewModelScope.launch(defaultDispatcher) {
            _isSaving.value = true
            _left.value = 1
            _done.value = 0
            when (val type = _type.value) {
                is Screen.ApngTools.Type.ApngToImage -> {
                    val results = mutableListOf<SaveResult>()
                    type.apngUri?.toString()?.also { apngUri ->
                        _left.value = 0
                        apngConverter.extractFramesFromApng(
                            apngUri = apngUri,
                            imageFormat = imageFormat,
                            quality = params.quality
                        ).onCompletion {
                            onResult(results, fileController.savingPath)
                        }.collect { uri ->
                            imageGetter.getImage(
                                data = uri,
                                originalSize = true
                            )?.let { localBitmap ->
                                if (done in imageFrames.getFramePositions(convertedImageUris.size + 10)) {
                                    val imageInfo = ImageInfo(
                                        imageFormat = imageFormat,
                                        width = localBitmap.width,
                                        height = localBitmap.height
                                    )

                                    results.add(
                                        fileController.save(
                                            saveTarget = ImageSaveTarget<ExifInterface>(
                                                imageInfo = imageInfo,
                                                originalUri = uri,
                                                sequenceNumber = _done.value + 1,
                                                data = imageCompressor.compressAndTransform(
                                                    image = localBitmap,
                                                    imageInfo = ImageInfo(
                                                        imageFormat = imageFormat,
                                                        quality = params.quality,
                                                        width = localBitmap.width,
                                                        height = localBitmap.height
                                                    )
                                                )
                                            ),
                                            keepOriginalMetadata = false
                                        )
                                    )
                                }
                            } ?: results.add(
                                SaveResult.Error.Exception(Throwable())
                            )
                            _done.value++
                        }
                    }
                }

                is Screen.ApngTools.Type.ImageToApng -> {
                    _left.value = type.imageUris?.size ?: -1
                    apngData = type.imageUris?.map { it.toString() }?.let { list ->
                        apngConverter.createApngFromImageUris(
                            imageUris = list,
                            params = params,
                            onProgress = {
                                _done.update { it + 1 }
                            }
                        ).also {
                            val timeStamp = SimpleDateFormat(
                                "yyyy-MM-dd_HH-mm-ss",
                                Locale.getDefault()
                            ).format(Date())
                            onApngSaveResult("APNG_$timeStamp")
                        }
                    }
                }

                is Screen.ApngTools.Type.ApngToJxl -> {
                    val results = mutableListOf<SaveResult>()
                    val apngUris = type.apngUris?.map {
                        it.toString()
                    } ?: emptyList()

                    _left.value = apngUris.size
                    apngConverter.convertApngToJxl(
                        apngUris = apngUris,
                        quality = jxlQuality
                    ) { uri, jxlBytes ->
                        results.add(
                            fileController.save(
                                saveTarget = JxlSaveTarget(uri, jxlBytes),
                                keepOriginalMetadata = true
                            )
                        )
                        _done.update { it + 1 }
                    }

                    onResult(results, fileController.savingPath)
                }

                null -> Unit
            }
            _isSaving.value = false
        }
    }

    private fun JxlSaveTarget(
        uri: String,
        jxlBytes: ByteArray
    ): SaveTarget = FileSaveTarget(
        originalUri = uri,
        filename = jxlFilename(uri),
        data = jxlBytes,
        imageFormat = ImageFormat.Jxl.Lossless
    )

    private fun jxlFilename(
        uri: String
    ): String = fileController.constructImageFilename(
        ImageSaveTarget<ExifInterface>(
            imageInfo = ImageInfo(
                imageFormat = ImageFormat.Jxl.Lossless,
                originalUri = uri
            ),
            originalUri = uri,
            sequenceNumber = done + 1,
            metadata = null,
            data = ByteArray(0)
        ),
        forceNotAddSizeInFilename = true
    )

    fun cancelSaving() {
        savingJob?.cancel()
        savingJob = null
        _isSaving.value = false
    }

    fun reorderImageUris(uris: List<Uri>?) {
        if (type is Screen.ApngTools.Type.ImageToApng) {
            _type.update {
                Screen.ApngTools.Type.ImageToApng(uris)
            }
        }
    }

    fun addImageToUris(uris: List<Uri>) {
        val type = _type.value
        if (type is Screen.ApngTools.Type.ImageToApng) {
            _type.update {
                val newUris = type.imageUris?.plus(uris)?.toSet()?.toList()

                Screen.ApngTools.Type.ImageToApng(newUris)
            }
        }
    }

    fun removeImageAt(index: Int) {
        val type = _type.value
        if (type is Screen.ApngTools.Type.ImageToApng) {
            _type.update {
                val newUris = type.imageUris?.toMutableList()?.apply {
                    removeAt(index)
                }

                Screen.ApngTools.Type.ImageToApng(newUris)
            }
        }
    }

    fun setImageFormat(imageFormat: ImageFormat) {
        _imageFormat.update { imageFormat }
    }

    fun setQuality(quality: Quality) {
        updateParams(params.copy(quality = quality))
    }

    fun updateParams(params: ApngParams) {
        _params.update { params }
    }

    fun performSharing(onComplete: () -> Unit) {
        _isSaving.value = false
        savingJob?.cancel()
        savingJob = viewModelScope.launch(defaultDispatcher) {
            _isSaving.value = true
            _left.value = 1
            _done.value = 0
            when (val type = _type.value) {
                is Screen.ApngTools.Type.ApngToImage -> {
                    _left.value = -1
                    val positions =
                        imageFrames.getFramePositions(convertedImageUris.size).map { it - 1 }
                    val uris = convertedImageUris.filterIndexed { index, _ ->
                        index in positions
                    }
                    shareProvider.shareUris(uris)
                    onComplete()
                }

                is Screen.ApngTools.Type.ImageToApng -> {
                    _left.value = type.imageUris?.size ?: -1
                    type.imageUris?.map { it.toString() }?.let { list ->
                        apngConverter.createApngFromImageUris(
                            imageUris = list,
                            params = params,
                            onProgress = {
                                _done.update { it + 1 }
                            }
                        ).also { byteArray ->
                            val timeStamp = SimpleDateFormat(
                                "yyyy-MM-dd_HH-mm-ss",
                                Locale.getDefault()
                            ).format(Date())
                            val apngName = "APNG_$timeStamp"
                            shareProvider.shareByteArray(
                                byteArray = byteArray,
                                filename = "$apngName.png",
                                onComplete = onComplete
                            )
                        }
                    }
                }

                is Screen.ApngTools.Type.ApngToJxl -> {
                    val results = mutableListOf<String?>()
                    val apngUris = type.apngUris?.map {
                        it.toString()
                    } ?: emptyList()

                    _left.value = apngUris.size
                    apngConverter.convertApngToJxl(
                        apngUris = apngUris,
                        quality = jxlQuality
                    ) { uri, jxlBytes ->
                        results.add(
                            shareProvider.cacheByteArray(
                                byteArray = jxlBytes,
                                filename = jxlFilename(uri)
                            )
                        )
                        _done.update { it + 1 }
                    }

                    shareProvider.shareUris(results.filterNotNull())
                }

                null -> Unit
            }
            _isSaving.value = false
        }
    }

    fun setJxlQuality(quality: Quality) {
        _jxlQuality.update {
            (quality as? Quality.Jxl) ?: Quality.Jxl()
        }
    }

}