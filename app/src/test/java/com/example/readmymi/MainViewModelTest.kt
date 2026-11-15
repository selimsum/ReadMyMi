package com.example.readmymi

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.example.readmymi.data.SensorDatabase
import com.example.readmymi.data.SensorDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class MainViewModelTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        SensorDatabase.setTestInstance(null)
        SensorForegroundService.liveSensorData.value = null
        SensorForegroundService.discoveredDevices.value = emptyMap()
    }

    private fun createTestViewModel(initialMac: String = ""): Pair<MainViewModel, SharedPreferences.Editor> {
        val application = mock<Application>()
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()

        whenever(application.applicationContext).thenReturn(context)
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        whenever(prefs.getString(anyString(), anyOrNull())).thenReturn(initialMac)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putBoolean(anyString(), anyBoolean())).thenReturn(editor)
        whenever(editor.putString(anyString(), anyString())).thenReturn(editor)

        val mockDbInstance = mock<SensorDatabase>()
        val mockDao = mock<SensorDao>()
        whenever(mockDbInstance.sensorDao()).thenReturn(mockDao)
        whenever(mockDao.getLatest(anyString())).thenReturn(emptyFlow())
        SensorDatabase.setTestInstance(mockDbInstance)

        return MainViewModel(application) to editor
    }

    @Test
    fun testUpdateLastMac() {
        val application = mock<Application>()
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()

        whenever(application.applicationContext).thenReturn(context)
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        whenever(prefs.getString(anyString(), anyOrNull())).thenReturn("old_mac")
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putString(anyString(), anyString())).thenReturn(editor)

        val mockDbInstance = mock<SensorDatabase>()
        val mockDao = mock<SensorDao>()
        whenever(mockDbInstance.sensorDao()).thenReturn(mockDao)
        whenever(mockDao.getLatest(anyString())).thenReturn(emptyFlow())
        SensorDatabase.setTestInstance(mockDbInstance)

        val viewModel = MainViewModel(application)

        // Check initial value from prefs
        assertEquals("old_mac", viewModel.lastMac.value)

        // Update to new mac
        viewModel.updateLastMac("00:11:22:33:44:55")

        // Verify stateflow is updated
        assertEquals("00:11:22:33:44:55", viewModel.lastMac.value)

        // Verify that prefs.lastMac was updated
        verify(editor).putString("last_mac", "00:11:22:33:44:55")
        verify(editor).apply()

        // Test setting the same mac does not update prefs again
        reset(editor)
        whenever(editor.putString(anyString(), anyString())).thenReturn(editor)
        viewModel.updateLastMac("00:11:22:33:44:55")
        verify(editor, never()).putString(anyString(), anyString())
    }

    @Test
    fun testSelectDeviceAndCompleteOnboarding() {
        val application = mock<Application>()
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()

        whenever(application.applicationContext).thenReturn(context)
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        whenever(prefs.getString(anyString(), anyOrNull())).thenReturn("")
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putBoolean(anyString(), anyBoolean())).thenReturn(editor)
        whenever(editor.putString(anyString(), anyString())).thenReturn(editor)

        val mockDbInstance = mock<SensorDatabase>()
        val mockDao = mock<SensorDao>()
        whenever(mockDbInstance.sensorDao()).thenReturn(mockDao)
        whenever(mockDao.getLatest(anyString())).thenReturn(emptyFlow())
        SensorDatabase.setTestInstance(mockDbInstance)

        val viewModel = MainViewModel(application)

        viewModel.selectDeviceAndCompleteOnboarding("11:22:33:44:55:66", "Living Room")

        assertEquals("11:22:33:44:55:66", viewModel.lastMac.value)
        verify(editor).putString("name_11:22:33:44:55:66", "Living Room")
        verify(editor).putString("last_mac", "11:22:33:44:55:66")
        verify(editor).putBoolean("has_completed_onboarding", true)
    }

    @Test
    fun testClearSelectionResetsMacAndServiceFlows() {
        val sample = SensorData(
            macAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Bedroom",
            temperature = 22.5,
            humidity = 48.0,
            battery = 95,
            timestamp = 1794220000000L
        )
        SensorForegroundService.liveSensorData.value = sample
        SensorForegroundService.discoveredDevices.value = mapOf(sample.macAddress to sample)

        val (viewModel, editor) = createTestViewModel(initialMac = sample.macAddress)
        assertEquals(sample.macAddress, viewModel.lastMac.value)

        viewModel.clearSelection()

        assertEquals("", viewModel.lastMac.value)
        verify(editor).putString("last_mac", "")
        assertNull(SensorForegroundService.liveSensorData.value)
        assertTrue(SensorForegroundService.discoveredDevices.value.isEmpty())
    }

    @Test
    fun testDownloadHistoryInitialStateAndNoMacGuard() {
        val (viewModel, _) = createTestViewModel(initialMac = "")
        assertFalse(viewModel.isDownloading.value)

        var callbackOk: Boolean? = null
        var callbackMessage: String? = null
        viewModel.downloadHistory(100) { ok, message ->
            callbackOk = ok
            callbackMessage = message
        }

        assertEquals(false, callbackOk)
        assertEquals("No sensor selected yet.", callbackMessage)
        assertFalse(viewModel.isDownloading.value)
    }
}
