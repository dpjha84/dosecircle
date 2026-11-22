package com.dosecircle.app.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecircle.app.AppContainer
import com.dosecircle.app.billing.BillingEvent
import com.dosecircle.app.billing.ProOffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PaywallViewModel(private val c: AppContainer) : ViewModel() {
    val offers: StateFlow<List<ProOffer>> = c.billing.offers
    val loading: StateFlow<Boolean> = c.billing.loading
    val events: SharedFlow<BillingEvent> = c.billing.events

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    init { reload() }

    fun reload() { viewModelScope.launch { c.billing.loadOffers() } }

    fun select(basePlanId: String) { _selected.value = basePlanId }

    fun purchase(activity: Activity, offer: ProOffer) = c.billing.purchase(activity, offer)

    fun restore(onResult: (Boolean) -> Unit) { viewModelScope.launch { onResult(c.billing.refreshEntitlement()) } }

    fun debugUnlock() { viewModelScope.launch { c.billing.setPro(true) } }
}
