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

class PrefsManagerTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var prefsManager: PrefsManager

    @Before
    fun setup() {
        context = mock()
        sharedPreferences = mock()
        editor = mock()

        whenever(context.getSharedPreferences("ReadMyMiPrefs", Context.MODE_PRIVATE))
            .thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)

        // Mock chainable editor methods
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.putInt(any(), any())).thenReturn(editor)
        whenever(editor.putBoolean(any(), any())).thenReturn(editor)
        whenever(editor.putFloat(any(), any())).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)

        prefsManager = PrefsManager(context)
    }

    @Test
    fun `getDeviceName returns stored name if exists`() {
        val mac = "00:11:22:33:44:55"
        val expectedName = "Living Room Temp"
        whenever(sharedPreferences.getString(eq("name_$mac"), any())).thenReturn(expectedName)

        val actualName = prefsManager.getDeviceName(mac)
        assertEquals(expectedName, actualName)
    }

    @Test
    fun `getDeviceName returns mac if stored name does not exist`() {
        val mac = "00:11:22:33:44:55"
        whenever(sharedPreferences.getString(eq("name_$mac"), any())).thenReturn(null)

        val actualName = prefsManager.getDeviceName(mac)
        assertEquals(mac, actualName)
    }

    @Test
    fun `setDeviceName stores name correctly`() {
        val mac = "00:11:22:33:44:55"
        val name = "Living Room Temp"

        prefsManager.setDeviceName(mac, name)

        verify(editor).putString("name_$mac", name)
        verify(editor).apply()
    }

    @Test
    fun `lastMac returns stored mac`() {
        val expectedMac = "AA:BB:CC:DD:EE:FF"
        whenever(sharedPreferences.getString(eq("last_mac"), any())).thenReturn(expectedMac)

        val actualMac = prefsManager.lastMac
        assertEquals(expectedMac, actualMac)
    }

    @Test
    fun `lastMac returns empty string if not stored`() {
        whenever(sharedPreferences.getString(eq("last_mac"), any())).thenReturn(null)

        val actualMac = prefsManager.lastMac
        assertEquals("", actualMac)
    }

    @Test
    fun `lastMac set stores mac correctly`() {
        val mac = "AA:BB:CC:DD:EE:FF"
        prefsManager.lastMac = mac

        verify(editor).putString("last_mac", mac)
        verify(editor).apply()
    }

    @Test
    fun `alertsEnabled returns stored boolean`() {
        whenever(sharedPreferences.getBoolean(eq("alerts_enabled"), any())).thenReturn(true)

        val actualEnabled = prefsManager.alertsEnabled
        assertEquals(true, actualEnabled)
    }

    @Test
    fun `alertsEnabled set stores boolean correctly`() {
        prefsManager.alertsEnabled = true

        verify(editor).putBoolean("alerts_enabled", true)
        verify(editor).apply()
    }
}
