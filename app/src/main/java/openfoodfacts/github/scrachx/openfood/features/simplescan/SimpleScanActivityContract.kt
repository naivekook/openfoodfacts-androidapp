package openfoodfacts.github.scrachx.openfood.features.simplescan

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import openfoodfacts.github.scrachx.openfood.models.Product

class SimpleScanActivityContract : ActivityResultContract<Unit, Product?>() {

    companion object {
        const val KEY_SCANNED_PRODUCT = "scanned_product"
    }

    override fun createIntent(context: Context, input: Unit?): Intent {
        return Intent(context, SimpleScanActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Product? {
        val bundle = intent?.extras ?: return null
        return if (resultCode == Activity.RESULT_OK && bundle.containsKey(KEY_SCANNED_PRODUCT)) {
            bundle.getSerializable(KEY_SCANNED_PRODUCT) as? Product
                ?: error("Unable to deserialize product from intent.")
        } else {
            null
        }
    }
}
