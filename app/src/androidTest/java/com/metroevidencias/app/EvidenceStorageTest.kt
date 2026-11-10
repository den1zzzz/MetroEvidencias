package com.metroevidencias.app

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.metroevidencias.app.domain.*
import com.metroevidencias.app.ui.EvidenceStorage
import com.metroevidencias.app.ui.evidenceRoot
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EvidenceStorageTest {
    @Test fun keepsOriginalAndRecoversStampedCopyBeforeConfirmation() {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot=File(target.cacheDir,"watermark-test").apply { mkdirs() }
        val context=object:ContextWrapper(target) { override fun getFilesDir()=testRoot }
        val id=UUID.randomUUID().toString()
        val record=WorkRecord(UUID.randomUUID().toString(),"2026-11-11T06:02:03Z",RecordDraft(
            workType=WorkType.FAILURE,description="PRUEBA",siteId="E20",locationConfirmed=true,
            tag="Extintores|Gabinetes",shift="Nocturno",workDate="2026-11-10"))
        EvidenceStorage.prepare(context,record,"test",id,Instant.parse("2026-11-11T06:02:03Z"))
        val original=EvidenceStorage.original(context,id)
        val image=Bitmap.createBitmap(1200,1600,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);canvas.drawColor(Color.rgb(115,131,140))
        canvas.drawText("PRUEBA DE EVIDENCIA",90f,170f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=55f })
        original.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG,95,it) };image.recycle()
        val bytes=original.readBytes()
        EvidenceStorage.release(id)
        EvidenceStorage.recover(context)
        assertArrayEquals(bytes,original.readBytes())
        val root=evidenceRoot(context)
        val marked=File(root,"$id.jpg");assertTrue(marked.length()>0)
        assertFalse(bytes.contentEquals(marked.readBytes()))
        val meta=JSONObject(File(root,"$id.json").readText())
        assertEquals("2026-11-10",meta.getString("workDate"))
        assertEquals("#11/11/2026 01:02:03",meta.getJSONArray("stampLines").getString(0))
        assertTrue(meta.getBoolean("requiresConfirmation"))
        assertFalse(File(root,"$id.ready").exists())
        assertEquals(1,EvidenceStorage.confirm(context,record.id,"test"))
        assertTrue(File(root,"$id.ready").exists())
        assertFalse(File(root,"$id.sent").exists())
        marked.copyTo(File(testRoot,"preview.jpg"),overwrite=true)
    }
}
