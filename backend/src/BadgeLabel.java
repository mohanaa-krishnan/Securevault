import javax.swing.*;
import java.awt.*;

/**
 * A small pill-shaped label for status/strength indicators
 * (e.g. "WEAK", "MODERATE", "STRONG").
 */
public class BadgeLabel extends JLabel {

    public BadgeLabel(String text, Color background, Color foreground) {
        super(text);
        setOpaque(false);
        setFont(UITheme.BODY_SMALL.deriveFont(Font.BOLD));
        setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        setForeground(foreground);
        putClientProperty("badgeBg", background);
    }

    public void setColors(Color background, Color foreground) {
        putClientProperty("badgeBg", background);
        setForeground(foreground);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color bg = (Color) getClientProperty("badgeBg");
        g2.setColor(bg != null ? bg : UITheme.getCanvasBg());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}
