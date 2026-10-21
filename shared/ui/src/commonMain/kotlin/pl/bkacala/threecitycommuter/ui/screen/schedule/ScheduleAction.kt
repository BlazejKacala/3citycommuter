package pl.bkacala.threecitycommuter.ui.screen.schedule

import kotlinx.datetime.LocalDate
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.SavedSchedule
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleAlert
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleCollection
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleProvider
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleTrip
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.SearchCategory

sealed interface ScheduleAction {
    data class QueryChanged(val value: String) : ScheduleAction
    data class CategoryChanged(val value: SearchCategory) : ScheduleAction
    data class ProviderChanged(val value: ScheduleProvider?) : ScheduleAction
    data class OpenLine(val id: String) : ScheduleAction
    data class OpenStop(val id: String) : ScheduleAction
    data class LineChanged(val id: String?) : ScheduleAction
    data class StopChanged(val id: String) : ScheduleAction
    data object ReverseDirection : ScheduleAction
    data class DirectionChanged(val id: String) : ScheduleAction
    data class DateChanged(val value: LocalDate) : ScheduleAction
    data class OpenTrip(val trip: ScheduleTrip) : ScheduleAction
    data class OpenCollection(val collection: ScheduleCollection) : ScheduleAction
    data class ToggleFavorite(val key: String) : ScheduleAction
    data object SaveOffline : ScheduleAction
    data class OpenSaved(val saved: SavedSchedule) : ScheduleAction
    data class RemoveSaved(val id: String) : ScheduleAction
    data class SaveAlert(val alert: ScheduleAlert) : ScheduleAction
    data class RemoveAlert(val tripId: String) : ScheduleAction
    data object ClearHistory : ScheduleAction
    data object MessageShown : ScheduleAction
    data class ScreenResumed(val destination: ScheduleDestination = ScheduleDestination.TIMETABLE) : ScheduleAction
    data object ScreenPaused : ScheduleAction
    data object Retry : ScheduleAction
}

enum class ScheduleDestination { HOME, TIMETABLE, TRIP, COLLECTION, NOTICES }
