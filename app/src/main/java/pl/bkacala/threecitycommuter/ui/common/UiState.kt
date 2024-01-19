package pl.bkacala.threecitycommuter.ui.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    class Error(val exception: Throwable) : UiState<Nothing>
    class Success<T>(val data: T) : UiState<T>
}

fun <T> Flow<T>.asUiState(): Flow<UiState<T>> {
    return this.map<T, UiState<T>> { UiState.Success(it) }
        .onStart { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
}
