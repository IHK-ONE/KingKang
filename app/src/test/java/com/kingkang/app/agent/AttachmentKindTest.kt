package com.kingkang.app.agent

import org.junit.Assert.assertEquals
import org.junit.Test

/** The preview dialog's type rules: MIME first, then the extension pickers often leave behind. */
class AttachmentKindTest {

    @Test
    fun `raster images preview inline`() {
        assertEquals(AttachmentKind.Image, attachmentKind("image/png", "photo.png"))
        assertEquals(AttachmentKind.Image, attachmentKind("IMAGE/JPEG", "IMG_0042"))
    }

    @Test
    fun `source files without a useful mime type still read as text`() {
        assertEquals(
            AttachmentKind.Text,
            attachmentKind("application/octet-stream", "exploit.py")
        )
        assertEquals(AttachmentKind.Text, attachmentKind("", "notes.md"))
        assertEquals(AttachmentKind.Text, attachmentKind("", "Dockerfile"))
    }

    @Test
    fun `svg markup is shown as text rather than decoded`() {
        assertEquals(AttachmentKind.Text, attachmentKind("image/svg+xml", "icon.svg"))
    }

    @Test
    fun `pdf and unknown binaries fall back to the system viewer`() {
        assertEquals(AttachmentKind.Pdf, attachmentKind("application/pdf", "report.pdf"))
        assertEquals(
            AttachmentKind.Other,
            attachmentKind("application/zip", "archive.zip")
        )
    }

    @Test
    fun `text mime subtypes count even without a known extension`() {
        assertEquals(AttachmentKind.Text, attachmentKind("text/x-python", "tool"))
        assertEquals(AttachmentKind.Text, attachmentKind("application/vnd.api+json", "data"))
    }
}
