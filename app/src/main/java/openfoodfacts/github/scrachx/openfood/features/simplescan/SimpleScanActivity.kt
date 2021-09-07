@file:Suppress("DEPRECATION")

package openfoodfacts.github.scrachx.openfood.features.simplescan

import android.hardware.Camera
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import openfoodfacts.github.scrachx.openfood.R
import openfoodfacts.github.scrachx.openfood.databinding.ActivitySimpleScanBinding
import openfoodfacts.github.scrachx.openfood.features.scan.MlKitCameraView
import openfoodfacts.github.scrachx.openfood.models.CameraState
import openfoodfacts.github.scrachx.openfood.repositories.ScannerPreferencesRepository
import java.util.concurrent.atomic.AtomicBoolean

@AndroidEntryPoint
class SimpleScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimpleScanBinding
    private val viewModel: SimpleScanViewModel by viewModels()

    private val mlKitView by lazy { MlKitCameraView(this) }
    private val scannerInitialized = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUI()

        binding.scanFlashBtn.setOnClickListener {
            viewModel.changeCameraFlash()
        }
        binding.scanMoreBtn.setOnClickListener {
            showMoreSettings(viewModel.scannerOptionsFlow.value)
        }

        // TODO
        // open hint dialog after 15sec of inactivity

        lifecycleScope.launch {
            viewModel.scannerOptionsFlow
                .flowWithLifecycle(lifecycle)
                .collect { options ->
                    if (!scannerInitialized.getAndSet(true)) {
                        setupBarcodeScanner(options)
                    }
                    applyScannerOptions(options)
                }
        }

        lifecycleScope.launch {
            viewModel.sideEffectsFlow
                .flowWithLifecycle(lifecycle)
                .collect { sideEffect ->
                    when (sideEffect) {
                        is SimpleScanViewModel.SideEffect.ProductFound -> {
                            // TODO
                        }
                        is SimpleScanViewModel.SideEffect.ProductNotFound -> {
                            // TODO
                        }
                        is SimpleScanViewModel.SideEffect.ScanTrouble -> {
                            // TODO
                        }
                    }
                }
        }

        lifecycleScope.launch {
            viewModel.progressFlow
                .flowWithLifecycle(lifecycle)
                .collect {
                    // TODO
                }
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.scannerOptionsFlow.value.mlScannerEnabled) {
            mlKitView.onResume()
        } else {
            binding.scanBarcodeView.resume()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // status bar will remain visible if user presses home and then reopens the activity
        // hence hiding status bar again
        hideSystemUI()
    }

    private fun hideSystemUI() {
        WindowInsetsControllerCompat(window, binding.root).hide(WindowInsetsCompat.Type.statusBars())
        actionBar?.hide()
    }

    private fun applyScannerOptions(options: SimpleScanScannerOptions) {
        // camera state
        if (options.mlScannerEnabled) {
            mlKitView.toggleCamera()
        } else {
            val cameraId = when (options.cameraState) {
                CameraState.Back -> Camera.CameraInfo.CAMERA_FACING_BACK
                CameraState.Front -> Camera.CameraInfo.CAMERA_FACING_FRONT
            }
            with(binding.scanBarcodeView) {
                pause()
                val newSettings = barcodeView.cameraSettings.apply {
                    requestedCameraId = cameraId
                }
                barcodeView.cameraSettings = newSettings
                resume()
            }
        }

        // flash
        val flashIconRes = if (options.flashEnabled) {
            R.drawable.ic_flash_off_white_24dp
        } else {
            R.drawable.ic_flash_on_white_24dp
        }
        binding.scanFlashBtn.setImageResource(flashIconRes)

        if (options.mlScannerEnabled) {
            mlKitView.updateFlashSetting(options.flashEnabled)
        } else {
            if (options.flashEnabled) {
                binding.scanBarcodeView.setTorchOn()
            } else {
                binding.scanBarcodeView.setTorchOff()
            }
        }

        // autofocus
        if (options.mlScannerEnabled) {
            mlKitView.updateFocusModeSetting(options.autoFocusEnabled)
        } else {
            with(binding.scanBarcodeView) {
                pause()
                val newSettings = barcodeView.cameraSettings.apply {
                    isAutoFocusEnabled = options.autoFocusEnabled
                }
                barcodeView.cameraSettings = newSettings
                resume()
            }
        }
    }

    private fun showMoreSettings(cameraOptions: SimpleScanScannerOptions) {
        PopupMenu(this, binding.scanMoreBtn)
            .apply {
                menuInflater.inflate(R.menu.simple_scan_menu, menu)
                menu.findItem(R.id.simple_scan_autofocus).isChecked = cameraOptions.autoFocusEnabled
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.simple_scan_autofocus -> viewModel.changeCameraAutoFocus()
                        R.id.simple_scan_trouble -> showManualBarcodeDialog()
                        R.id.simple_scan_switch_camera -> viewModel.changeCameraState()
                    }
                    true
                }
            }
            .show()
    }

    private fun setupBarcodeScanner(options: SimpleScanScannerOptions) {
        binding.scanBarcodeView.isVisible = !options.mlScannerEnabled
        binding.scanMlView.isVisible = options.mlScannerEnabled

        if (options.mlScannerEnabled) {
            mlKitView.attach(binding.scanMlView, options.cameraState.value, options.flashEnabled, options.autoFocusEnabled)
            mlKitView.barcodeScannedCallback = {
                viewModel.findProduct(it)
            }
        } else {
            with(binding.scanBarcodeView) {
                barcodeView.decoderFactory = DefaultDecoderFactory(ScannerPreferencesRepository.BARCODE_FORMATS)
                setStatusText(null)
                barcodeView.cameraSettings.requestedCameraId = options.cameraState.value
                barcodeView.cameraSettings.isAutoFocusEnabled = options.autoFocusEnabled

                decodeSingle(object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult?) {
                        viewModel.findProduct(result?.text)
                    }

                    override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
                })
            }
        }
    }

    private fun showManualBarcodeDialog() {
        // TODO
    }
}