package pl.bkacala.threecitycommuter.utils

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow

fun <T> Flow<T>.stateInViewModelScope(
    viewModel: ViewModel,
    scope: CoroutineScope = viewModel.viewModelScope,
    started: SharingStarted = SharingStarted.WhileSubscribed(5_000),
    initialValue: T
): StateFlow<T> {
    return this.stateIn(scope, started, initialValue)
}
