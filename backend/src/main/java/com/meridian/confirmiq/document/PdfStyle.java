package com.meridian.confirmiq.document;

import com.lowagie.text.Font;

import java.awt.Color;

/** Shared typography and palette for generated documents. */
final class PdfStyle {

    static final Color NAVY = new Color(0x10, 0x2A, 0x43);
    static final Color ACCENT = new Color(0x1F, 0x6F, 0xB2);
    static final Color MUTED = new Color(0x5B, 0x67, 0x78);
    static final Color RULE = new Color(0xD5, 0xDB, 0xE3);
    static final Color LABEL_BG = new Color(0xF3, 0xF5, 0xF8);
    static final Color STAMP = new Color(0x1B, 0x5E, 0x9B);

    static final Font TITLE = new Font(Font.HELVETICA, 14, Font.BOLD, NAVY);
    static final Font SUBTITLE = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, MUTED);
    static final Font H2 = new Font(Font.HELVETICA, 10, Font.BOLD, NAVY);
    static final Font BODY = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    static final Font BODY_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    static final Font SMALL = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, MUTED);
    static final Font SMALL_BOLD = new Font(Font.HELVETICA, 7.5f, Font.BOLD, NAVY);
    static final Font LABEL = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, MUTED);
    static final Font VALUE = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    static final Font TABLE_HEAD = new Font(Font.HELVETICA, 7.5f, Font.BOLD, Color.WHITE);

    private PdfStyle() {}
}
