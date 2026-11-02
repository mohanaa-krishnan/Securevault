import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.function.IntPredicate;

/**
 * Renders the credentials table with alternating row tint, a monospace
 * password column, and a selection highlight that matches UITheme.
 */
public class VaultTableRenderer extends DefaultTableCellRenderer {

    private final int passwordColumnIndex;
    private final IntPredicate isRowRevealed;

    public VaultTableRenderer(int passwordColumnIndex, IntPredicate isRowRevealed) {
        this.passwordColumnIndex = passwordColumnIndex;
        this.isRowRevealed = isRowRevealed;
    }

    public VaultTableRenderer(int passwordColumnIndex) {
        this(passwordColumnIndex, row -> false);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        String displayValue = (value != null) ? value.toString() : "";
        if (column == passwordColumnIndex && !displayValue.isEmpty() && !isRowRevealed.test(row)) {
            displayValue = "••••••••••••";
        }

        JLabel label = (JLabel) super.getTableCellRendererComponent(
            table, displayValue, isSelected, hasFocus, row, column);

        label.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        label.setFont(column == passwordColumnIndex ? UITheme.MONO : UITheme.BODY);

        if (isSelected) {
            label.setBackground(table.getSelectionBackground());
            label.setForeground(table.getSelectionForeground());
        } else {
            label.setBackground(row % 2 == 0 ? UITheme.getSurfaceBg() : UITheme.getCanvasBg());
            label.setForeground(UITheme.getTextPrimary());
        }
        return label;
    }
}
