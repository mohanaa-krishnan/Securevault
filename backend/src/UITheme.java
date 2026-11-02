import java.awt.*;
import javax.swing.UIManager;

/**
 * Central design tokens for SecureVault's Swing UI.
 * Change a value here, it changes everywhere — no more scattered inline styling.
 */
public final class UITheme {

    private UITheme() {}

    // ---- Color palette (Default Light Tokens) ----
    public static final Color BG_CANVAS = new Color(0xF7F6F3);
    public static final Color BG_SURFACE = Color.WHITE;
    public static final Color BORDER = new Color(0xEAEAEA);
    public static final Color TEXT_PRIMARY = new Color(0x2F3437);
    public static final Color TEXT_SECONDARY = new Color(0x787774);
    public static final Color ACCENT = new Color(0x111111);
    public static final Color ACCENT_HOVER = new Color(0x333333);
    public static final Color SUCCESS_BG = new Color(0xEDF3EC);
    public static final Color SUCCESS_TEXT = new Color(0x346538);
    public static final Color DANGER_BG = new Color(0xFDEBEC);
    public static final Color DANGER_TEXT = new Color(0x9F2F2D);
    public static final Color WARNING_BG = new Color(0xFBF3DB);
    public static final Color WARNING_TEXT = new Color(0x956400);
    public static final Color INFO_BG = new Color(0xE1F3FE);
    public static final Color INFO_TEXT = new Color(0x1F6C9F);

    // ---- Typography ----
    public static final String FONT_FAMILY = "Segoe UI"; // falls back cleanly on Linux/macOS
    public static final Font HEADING_XL = new Font(FONT_FAMILY, Font.BOLD, 26);
    public static final Font HEADING_L = new Font(FONT_FAMILY, Font.BOLD, 20);
    public static final Font HEADING_M = new Font(FONT_FAMILY, Font.BOLD, 15);
    public static final Font BODY = new Font(FONT_FAMILY, Font.PLAIN, 13);
    public static final Font BODY_SMALL = new Font(FONT_FAMILY, Font.PLAIN, 12);
    public static final Font MONO = new Font("JetBrains Mono", Font.PLAIN, 13);

    // ---- Spacing scale (multiples of 4px) ----
    public static final int SPACE_XS = 4;
    public static final int SPACE_S = 8;
    public static final int SPACE_M = 16;
    public static final int SPACE_L = 24;
    public static final int SPACE_XL = 32;

    // ---- Shape ----
    public static final int RADIUS_SM = 4;
    public static final int RADIUS_MD = 8;

    // ---- Dynamic theme helpers for seamless dark mode ----
    public static boolean isDark() {
        return UIManager.getLookAndFeel() instanceof com.formdev.flatlaf.FlatDarkLaf;
    }

    public static Color getCanvasBg() {
        return isDark() ? new Color(0x1E1F22) : BG_CANVAS;
    }

    public static Color getSurfaceBg() {
        return isDark() ? new Color(0x2B2D30) : BG_SURFACE;
    }

    public static Color getBorderColor() {
        return isDark() ? new Color(0x3C3F41) : BORDER;
    }

    public static Color getTextPrimary() {
        return isDark() ? new Color(0xDFE1E5) : TEXT_PRIMARY;
    }

    public static Color getTextSecondary() {
        return isDark() ? new Color(0x9DA0A8) : TEXT_SECONDARY;
    }

    public static Color getBadgeSuccessBg() {
        return isDark() ? new Color(0x253B2F) : SUCCESS_BG;
    }

    public static Color getBadgeSuccessText() {
        return isDark() ? new Color(0x59A869) : SUCCESS_TEXT;
    }

    public static Color getBadgeWarningBg() {
        return isDark() ? new Color(0x423924) : WARNING_BG;
    }

    public static Color getBadgeWarningText() {
        return isDark() ? new Color(0xE0AA38) : WARNING_TEXT;
    }

    public static Color getBadgeDangerBg() {
        return isDark() ? new Color(0x442629) : DANGER_BG;
    }

    public static Color getBadgeDangerText() {
        return isDark() ? new Color(0xE05555) : DANGER_TEXT;
    }
}
