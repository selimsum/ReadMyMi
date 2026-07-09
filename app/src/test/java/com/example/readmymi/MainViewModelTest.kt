package com.example.readmymi

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.example.readmymi.data.SensorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

class MainViewModelTest {

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
}
