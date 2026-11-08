package com.example.readmymi

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.example.readmymi.data.SensorDatabase
import com.example.readmymi.data.SensorDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

        mockStatic(SensorDatabase::class.java).use { mockedDb ->
            val mockDbInstance = mock<SensorDatabase>()
            val mockDao = mock<SensorDao>()
            whenever(mockDbInstance.sensorDao()).thenReturn(mockDao)
            mockedDb.`when`<SensorDatabase> { SensorDatabase.getDatabase(context) }.thenReturn(mockDbInstance)

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
    }

    @Test
    fun testDemoModeToggle() {
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

        mockStatic(SensorDatabase::class.java).use { mockedDb ->
            val mockDbInstance = mock<SensorDatabase>()
            val mockDao = mock<SensorDao>()
            whenever(mockDbInstance.sensorDao()).thenReturn(mockDao)
            mockedDb.`when`<SensorDatabase> { SensorDatabase.getDatabase(context) }.thenReturn(mockDbInstance)

            val viewModel = MainViewModel(application)

            assertFalse(viewModel.isDemoMode.value)

            viewModel.enableDemoMode()
            assertTrue(viewModel.isDemoMode.value)
            verify(editor).putBoolean("has_completed_onboarding", true)

            viewModel.exitDemoMode()
            assertFalse(viewModel.isDemoMode.value)
            verify(editor).putBoolean("has_completed_onboarding", false)
        }
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

        mockStatic(SensorDatabase::class.java).use { mockedDb ->
            val mockDbInstance = mock<SensorDatabase>()
            val mockDao = mock<SensorDao>()
            whenever(mockDbInstance.sensorDao()).thenReturn(mockDao)
            mockedDb.`when`<SensorDatabase> { SensorDatabase.getDatabase(context) }.thenReturn(mockDbInstance)

            val viewModel = MainViewModel(application)

            viewModel.selectDeviceAndCompleteOnboarding("11:22:33:44:55:66", "Living Room")

            assertEquals("11:22:33:44:55:66", viewModel.lastMac.value)
            verify(editor).putString("name_11:22:33:44:55:66", "Living Room")
            verify(editor).putString("last_mac", "11:22:33:44:55:66")
            verify(editor).putBoolean("has_completed_onboarding", true)
        }
    }
}
