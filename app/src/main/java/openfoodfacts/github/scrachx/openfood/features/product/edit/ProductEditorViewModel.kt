package openfoodfacts.github.scrachx.openfood.features.product.edit

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import openfoodfacts.github.scrachx.openfood.models.Product

class ProductEditorViewModel: ViewModel() {

    private val productFlow = MutableStateFlow<State>(State.None)

    fun updateProduct(product: Product) {
        productFlow.value = State.Data(product)
    }

    fun observeState() : StateFlow<State> = productFlow

    fun save() {

    }

    private fun checkOfflineStorage() {
        offlineRepository.getOfflineProductByBarcode(productState.product!!.code)
    }

    private fun validateProduct() {

    }

    sealed class State{
        object None: State()
        data class Data(val value: Product): State()
    }
}
