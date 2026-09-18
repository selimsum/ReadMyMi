package com.example.readmymi

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PrefsManagerHistoryCheckedTest {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var prefsManager: PrefsManager

    @Before
    fun setup() {
        val context = mock<Context>()
        sharedPreferences = mock()
        editor = mock()
        whenever(context.getSharedPreferences("ReadMyMiPrefs", Context.MODE_PRIVATE))
            .thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putBoolean(any(), any())).thenReturn(editor)
        prefsManager = PrefsManager(context)
    }

    @Test
    fun historyCheckedFlagRoundTripsPerDevice() {
        val mac = "AA:BB:CC:DD:EE:FF"
        whenever(sharedPreferences.getBoolean(eq("history_checked_$mac"), any())).thenReturn(false)
        assertEquals(false, prefsManager.getHistoryChecked(mac))

        prefsManager.setHistoryChecked(mac, true)
        verify(editor).putBoolean("history_checked_$mac", true)
    }

    @Test
    fun alertActiveUsesSynchronousCommit() {
        val mac = "AA:BB:CC:DD:EE:FF"
        whenever(editor.commit()).thenReturn(true)
        prefsManager.setAlertActive(mac, "temp_high", true)
        verify(editor).putBoolean("alert_active_$mac:temp_high", true)
        verify(editor).commit()
    }
}
