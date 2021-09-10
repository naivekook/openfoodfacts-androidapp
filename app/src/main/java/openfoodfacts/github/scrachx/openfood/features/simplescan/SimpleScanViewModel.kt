package openfoodfacts.github.scrachx.openfood.features.simplescan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import openfoodfacts.github.scrachx.openfood.models.CameraState
import openfoodfacts.github.scrachx.openfood.models.Product
import openfoodfacts.github.scrachx.openfood.network.OpenFoodAPIClient
import openfoodfacts.github.scrachx.openfood.repositories.ScannerPreferencesRepository
import openfoodfacts.github.scrachx.openfood.utils.CoroutineDispatchers
import openfoodfacts.github.scrachx.openfood.utils.Utils
import javax.inject.Inject

@HiltViewModel
class SimpleScanViewModel @Inject constructor(
    private val coroutineDispatchers: CoroutineDispatchers,
    private val scannerPrefsRepository: ScannerPreferencesRepository,
    private val openFoodAPIClient: OpenFoodAPIClient
) : ViewModel() {

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
        viewModelScope.launch {
            if (barcode.isNullOrEmpty()) {
                _sideEffectsFlow.emit(SideEffect.ScanTrouble)
            } else {
                val sideEffect = withContext(coroutineDispatchers.io()) {
                    try {
                        val product = openFoodAPIClient.getProductStateFull(barcode, userAgent = Utils.HEADER_USER_AGENT_SCAN).product
                        if (product == null) {
                            SideEffect.ProductNotFound
                        } else {
                            SideEffect.ProductFound(product)
                        }
                    } catch (t: Throwable) {
                        Log.w("SimpleScanViewModel", t.message, t)
                        SideEffect.ConnectionError
                    }
                }
                _sideEffectsFlow.emit(sideEffect)
            }
        }
    }

    fun troubleScanningPressed() {
        viewModelScope.launch {
            _sideEffectsFlow.emit(SideEffect.ScanTrouble)
        }
    }

    sealed class SideEffect {
        data class ProductFound(val product: Product) : SideEffect()
        object ProductNotFound : SideEffect()
        object ScanTrouble : SideEffect()
        object ConnectionError : SideEffect()
    }
}
