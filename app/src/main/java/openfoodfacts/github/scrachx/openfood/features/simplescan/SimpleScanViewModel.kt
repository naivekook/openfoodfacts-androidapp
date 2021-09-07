package openfoodfacts.github.scrachx.openfood.features.simplescan

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import openfoodfacts.github.scrachx.openfood.models.CameraState
import openfoodfacts.github.scrachx.openfood.models.Product
import openfoodfacts.github.scrachx.openfood.repositories.ScannerPreferencesRepository
import openfoodfacts.github.scrachx.openfood.utils.CoroutineDispatchers
import javax.inject.Inject

@HiltViewModel
class SimpleScanViewModel @Inject constructor(
    private val coroutineDispatchers: CoroutineDispatchers,
    private val scannerPrefsRepository: ScannerPreferencesRepository,
) : ViewModel() {

    private val _progressFlow = MutableStateFlow(false)
    val progressFlow = _progressFlow.asStateFlow()

    private val _sideEffectsFlow = MutableSharedFlow<SideEffect>()
    val sideEffectsFlow = _sideEffectsFlow.asSharedFlow()

    private val _scannerOptionsFlow = MutableStateFlow(
        SimpleScanScannerOptions(
            mlScannerEnabled = scannerPrefsRepository.isMlScannerEnabled(),
            cameraState = scannerPrefsRepository.getCameraPref(),
            autoFocusEnabled = scannerPrefsRepository.getAutoFocusPref(),
            flashEnabled = scannerPrefsRepository.getFlashPref()
        )
    )
    val scannerOptionsFlow = _scannerOptionsFlow.asStateFlow()

    fun changeCameraAutoFocus() {
        val newValue = !_scannerOptionsFlow.value.autoFocusEnabled
        scannerPrefsRepository.saveAutoFocusPref(newValue)
        _scannerOptionsFlow.value = _scannerOptionsFlow.value.copy(
            autoFocusEnabled = newValue
        )
    }

    fun changeCameraFlash() {
        val newValue = !_scannerOptionsFlow.value.flashEnabled
        scannerPrefsRepository.saveFlashPref(newValue)
        _scannerOptionsFlow.value = _scannerOptionsFlow.value.copy(
            flashEnabled = newValue
        )
    }

    fun changeCameraState() {
        val newValue = when (_scannerOptionsFlow.value.cameraState) {
            CameraState.Front -> CameraState.Back
            CameraState.Back -> CameraState.Front
        }
        scannerPrefsRepository.saveCameraPref(newValue)
        _scannerOptionsFlow.value = _scannerOptionsFlow.value.copy(
            cameraState = newValue
        )
    }

    fun findProduct(barcode: String?) {

    }

    sealed class SideEffect {
        data class ProductFound(val product: Product) : SideEffect()
        object ProductNotFound : SideEffect()
        object ScanTrouble : SideEffect()
    }
}