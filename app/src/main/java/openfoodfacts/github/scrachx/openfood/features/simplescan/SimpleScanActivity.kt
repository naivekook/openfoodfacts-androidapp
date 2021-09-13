@file:Suppress("DEPRECATION")

package openfoodfacts.github.scrachx.openfood.features.simplescan

import android.content.Intent
import android.hardware.Camera
import android.os.Bundle
import android.text.InputType
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
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
import openfoodfacts.github.scrachx.openfood.features.simplescan.SimpleScanActivityContract.Companion.KEY_SCANNED_PRODUCT
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
        binding.scanFlipCameraBtn.setOnClickListener {
            viewModel.changeCameraState()
        }
        binding.scanChangeFocusBtn.setOnClickListener {
            viewModel.changeCameraAutoFocus()
        }
        binding.troubleScanningBtn.setOnClickListener {
            viewModel.troubleScanningPressed()
        }

        lifecycleScope.launch {
            viewModel.scannerOptionsFlow
                .flowWithLifecycle(lifecycle)
                .collect { options ->
                    Log.d("SimpleScanActivity", "options: $options")
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
                    Log.d("SimpleScanActivity", "sideEffect: $sideEffect")
                    when (sideEffect) {
                        is SimpleScanViewModel.SideEffect.ProductFound -> {
                            val intent = Intent().putExtra(KEY_SCANNED_PRODUCT, sideEffect.product)
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                        is SimpleScanViewModel.SideEffect.ProductNotFound -> {
                            stopScanning()
                            showProductNotFoundDialog()
                        }
                        is SimpleScanViewModel.SideEffect.ScanTrouble -> {
                            stopScanning()
                            showManualInputDialog()
                        }
                        SimpleScanViewModel.SideEffect.ConnectionError -> {
                            stopScanning()
                            showConnectionErrorDialog()
                        }
                    }
                }
        }
    }

    override fun onResume() {
        super.onResume()
        startScanning()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // status bar will remain visible if user presses home and then reopens the activity
        // hence hiding status bar again
        hideSystemUI()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        setResult(RESULT_CANCELED)
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
        val focusIconRes = if (options.autoFocusEnabled) {
            R.drawable.ic_baseline_camera_focus_on_24
        } else {
            R.drawable.ic_baseline_camera_focus_off_24
        }
        binding.scanChangeFocusBtn.setImageResource(focusIconRes)
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

                decodeContinuous(object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult?) {
                        viewModel.findProduct(result?.text)
                    }

                    override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
                })
            }
        }
    }

    private fun stopScanning() {
        if (viewModel.scannerOptionsFlow.value.mlScannerEnabled) {
            mlKitView.stopCameraPreview()
        } else {
            binding.scanBarcodeView.pause()
        }
    }

    private fun startScanning() {
        if (viewModel.scannerOptionsFlow.value.mlScannerEnabled) {
            mlKitView.onResume()
            mlKitView.startCameraPreview()
        } else {
            binding.scanBarcodeView.resume()
        }
    }

    private fun showManualInputDialog() {
        MaterialDialog.Builder(this@SimpleScanActivity)
            .title(R.string.trouble_scanning)
            .content(R.string.enter_barcode)
            .input(null, null, false) { _, input ->
                viewModel.findProduct(input.toString())
            }
            .inputType(InputType.TYPE_CLASS_NUMBER)
            .positiveText(R.string.ok_button)
            .negativeText(R.string.cancel_button)
            .onNegative { _, _ ->
                startScanning()
            }
            .build()
            .show()
    }

    private fun showProductNotFoundDialog() {
        MaterialDialog.Builder(this@SimpleScanActivity)
            .title(R.string.product_not_found_title)
            .content(R.string.product_not_found_description)
            .positiveText(R.string.txtYes)
            .onPositive { _, _ ->
                startScanning()
            }
            .negativeText(R.string.no_thx)
            .onNegative { _, _ ->
                setResult(RESULT_CANCELED)
                finish()
            }
            .build()
            .show()
    }

    private fun showConnectionErrorDialog() {
        MaterialDialog.Builder(this@SimpleScanActivity)
            .title(R.string.alert_dialog_warning_title)
            .content(R.string.txtConnectionError)
            .positiveText(R.string.retry)
            .onPositive { _, _ ->
                startScanning()
            }
            .negativeText(R.string.title_exit)
            .onNegative { _, _ ->
                setResult(RESULT_CANCELED)
                finish()
            }
            .show()
    }
}
