package ru.tech.imageresizershrinker.coredomain.use_case.edit_settings

import ru.tech.imageresizershrinker.coredomain.repository.SettingsRepository
import javax.inject.Inject

class ToggleScreensSearchEnabledUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke() = settingsRepository.toggleScreensSearchEnabled()
}