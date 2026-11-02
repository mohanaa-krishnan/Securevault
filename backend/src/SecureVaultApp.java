import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SecureVault Desktop Application — Modern Minimalist Dark Mode Swing UI.
 *
 * Features:
 *   - FlatLaf modern UI engine with live Dark/Light mode switching.
 *   - Centralized UITheme design tokens for consistent colors, fonts, and spacing.
 *   - Pill-shaped BadgeLabel password strength meter.
 *   - Custom VaultTableRenderer with alternating row tint, monospace passwords, and individual show/hide.
 *   - Ephemeral session key handling (AES-256 vault key is wiped on logout or app exit).
 *   - Auto-clearing clipboard (30s timeout) for copied credentials.
 *   - Subtle screen transitions between login and dashboard views.
 */
public class SecureVaultApp extends JFrame {

    // View identifiers
    private static final String VIEW_AUTH = "AUTH";
    private static final String VIEW_VAULT = "VAULT";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rootPanel = new JPanel(cardLayout);

    // Active session state (in-memory only)
    private int currentUserId = -1;
    private String currentUsername = null;
    private byte[] currentVaultKey = null;

    // Vault view components
    private DefaultTableModel tableModel;
    private JTable credentialsTable;
    private JTextField searchField;
    private JLabel userWelcomeLabel;
    private final List<VaultDao.VaultEntry> cachedEntries = new ArrayList<>();
    private boolean passwordsRevealed = false;
    private final Set<Integer> revealedEntryIds = new HashSet<>();
    private String lastCopiedPassword = null;
    private Timer clipboardClearTimer = null;

    public SecureVaultApp() {
        super("SecureVault — Password Manager");
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(980, 680);
        setMinimumSize(new Dimension(840, 540));
        setLocationRelativeTo(null);
        getRootPane().putClientProperty("JRootPane.menuBarEmbedded", true);

        // Security: wipe memory on window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                logout();
                dispose();
                System.exit(0);
            }
        });

        rootPanel.add(createAuthPanel(), VIEW_AUTH);
        rootPanel.add(createVaultPanel(), VIEW_VAULT);
        add(rootPanel);

        cardLayout.show(rootPanel, VIEW_AUTH);
    }

    // ==================================================================
    // Authentication View (Login & Registration)
    // ==================================================================

    private JPanel createAuthPanel() {
        JPanel authContainer = new JPanel(new GridBagLayout());
        authContainer.setBackground(UITheme.getCanvasBg());

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.getBorderColor(), 1, true),
            new EmptyBorder(30, 40, 30, 40)
        ));
        card.setBackground(UITheme.getSurfaceBg());
        card.setPreferredSize(new Dimension(440, 580));

        // Header row with logo and theme toggle
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JLabel titleLabel = new JLabel("SecureVault");
        titleLabel.setFont(UITheme.HEADING_XL);
        titleLabel.setForeground(UITheme.getTextPrimary());

        JButton quickThemeToggle = new JButton(UITheme.isDark() ? "☀" : "🌙");
        quickThemeToggle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        quickThemeToggle.setToolTipText("Toggle Dark/Light Mode");
        quickThemeToggle.setFocusPainted(false);
        quickThemeToggle.addActionListener(e -> toggleTheme(quickThemeToggle));

        topBar.add(titleLabel, BorderLayout.WEST);
        topBar.add(quickThemeToggle, BorderLayout.EAST);

        JLabel subtitleLabel = new JLabel("Encrypted Password Manager");
        subtitleLabel.setFont(UITheme.BODY);
        subtitleLabel.setForeground(UITheme.getTextSecondary());
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.HEADING_M);
        tabbedPane.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Login & Register Tabs
        tabbedPane.addTab("Unlock Vault", createLoginTab());
        tabbedPane.addTab("Create Account", createRegisterTab());

        card.add(topBar);
        card.add(Box.createVerticalStrut(UITheme.SPACE_XS));
        card.add(subtitleLabel);
        card.add(Box.createVerticalStrut(UITheme.SPACE_L));
        card.add(tabbedPane);

        authContainer.add(card);
        return authContainer;
    }

    private JPanel createLoginTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 10, 10, 10));
        panel.setBackground(UITheme.getSurfaceBg());

        JTextField userField = new JTextField();
        userField.setFont(UITheme.BODY);
        userField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JPasswordField passField = new JPasswordField();
        passField.setFont(UITheme.BODY);
        passField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JButton loginButton = new JButton("Unlock Vault");
        loginButton.setFont(UITheme.HEADING_M);
        loginButton.setBackground(new Color(13, 110, 253));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setForeground(new Color(220, 53, 69));
        statusLabel.setFont(UITheme.BODY_SMALL);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        loginButton.addActionListener(e -> {
            String username = userField.getText().trim();
            char[] password = passField.getPassword();

            if (username.isEmpty() || password.length == 0) {
                statusLabel.setText("Please enter username and master password.");
                return;
            }

            statusLabel.setText("Authenticating & deriving encryption key...");
            loginButton.setEnabled(false);

            SwingWorker<byte[], Void> worker = new SwingWorker<>() {
                @Override
                protected byte[] doInBackground() throws Exception {
                    return UserDao.authenticate(username, password);
                }

                @Override
                protected void done() {
                    loginButton.setEnabled(true);
                    try {
                        byte[] key = get();
                        int uid = UserDao.getUserId(username);
                        onLoginSuccess(uid, username, key);
                        userField.setText("");
                        passField.setText("");
                        statusLabel.setText(" ");
                    } catch (Exception ex) {
                        statusLabel.setText("Invalid username or master password.");
                    }
                }
            };
            worker.execute();
        });

        panel.add(createFieldLabel("Username:"));
        panel.add(userField);
        panel.add(Box.createVerticalStrut(UITheme.SPACE_M));
        panel.add(createFieldLabel("Master Password:"));
        panel.add(createPasswordFieldWithToggle(passField));
        panel.add(Box.createVerticalStrut(UITheme.SPACE_M));
        panel.add(statusLabel);
        panel.add(Box.createVerticalStrut(UITheme.SPACE_S));
        panel.add(loginButton);

        return panel;
    }

    private JPanel createRegisterTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(15, 10, 10, 10));
        panel.setBackground(UITheme.getSurfaceBg());

        JTextField userField = new JTextField();
        userField.setFont(UITheme.BODY);
        userField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JPasswordField passField = new JPasswordField();
        passField.setFont(UITheme.BODY);
        passField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JPasswordField confirmField = new JPasswordField();
        confirmField.setFont(UITheme.BODY);
        confirmField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        // Password strength meter
        JProgressBar strengthBar = new JProgressBar(0, 5);
        strengthBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        strengthBar.setValue(0);
        strengthBar.setStringPainted(false);

        // Status badge
        BadgeLabel strengthBadge = new BadgeLabel("NO PASSWORD", UITheme.getCanvasBg(), UITheme.getTextSecondary());

        passField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateStrength(); }
            public void removeUpdate(DocumentEvent e) { updateStrength(); }
            public void changedUpdate(DocumentEvent e) { updateStrength(); }

            private void updateStrength() {
                char[] p = passField.getPassword();
                int score = calculatePasswordScore(new String(p));
                Arrays.fill(p, '\0');
                strengthBar.setValue(score);
                if (score == 0) {
                    strengthBar.setForeground(UITheme.getTextSecondary());
                    strengthBadge.setText("EMPTY");
                    strengthBadge.setColors(UITheme.getCanvasBg(), UITheme.getTextSecondary());
                } else if (score <= 2) {
                    strengthBar.setForeground(UITheme.getBadgeDangerText());
                    strengthBadge.setText("WEAK");
                    strengthBadge.setColors(UITheme.getBadgeDangerBg(), UITheme.getBadgeDangerText());
                } else if (score <= 4) {
                    strengthBar.setForeground(UITheme.getBadgeWarningText());
                    strengthBadge.setText("MODERATE");
                    strengthBadge.setColors(UITheme.getBadgeWarningBg(), UITheme.getBadgeWarningText());
                } else {
                    strengthBar.setForeground(UITheme.getBadgeSuccessText());
                    strengthBadge.setText("STRONG");
                    strengthBadge.setColors(UITheme.getBadgeSuccessBg(), UITheme.getBadgeSuccessText());
                }
            }
        });

        JPanel strengthRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        strengthRow.setOpaque(false);
        strengthRow.add(new JLabel("Strength:"));
        strengthRow.add(strengthBadge);

        JButton registerButton = new JButton("Create Vault");
        registerButton.setFont(UITheme.HEADING_M);
        registerButton.setBackground(new Color(25, 135, 84));
        registerButton.setForeground(Color.WHITE);
        registerButton.setFocusPainted(false);
        registerButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        registerButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setForeground(new Color(220, 53, 69));
        statusLabel.setFont(UITheme.BODY_SMALL);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        registerButton.addActionListener(e -> {
            String username = userField.getText().trim();
            char[] pass = passField.getPassword();
            char[] confirm = confirmField.getPassword();

            if (username.isEmpty() || pass.length == 0) {
                statusLabel.setText("All fields are required.");
                Arrays.fill(pass, '\0');
                Arrays.fill(confirm, '\0');
                return;
            }

            if (!Arrays.equals(pass, confirm)) {
                statusLabel.setText("Passwords do not match.");
                Arrays.fill(pass, '\0');
                Arrays.fill(confirm, '\0');
                return;
            }

            if (pass.length < 8) {
                statusLabel.setText("Master password must be at least 8 characters.");
                Arrays.fill(pass, '\0');
                Arrays.fill(confirm, '\0');
                return;
            }

            registerButton.setEnabled(false);
            statusLabel.setText("Deriving keys (310,000 PBKDF2 iterations)...");

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    UserDao.register(username, pass);
                    return null;
                }

                @Override
                protected void done() {
                    registerButton.setEnabled(true);
                    Arrays.fill(confirm, '\0');
                    try {
                        get();
                        JOptionPane.showMessageDialog(
                            SecureVaultApp.this,
                            "Account created successfully! You can now unlock your vault.",
                            "Account Created",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                        userField.setText("");
                        passField.setText("");
                        confirmField.setText("");
                        statusLabel.setText(" ");
                    } catch (Exception ex) {
                        statusLabel.setText(ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                    }
                }
            };
            worker.execute();
        });

        panel.add(createFieldLabel("Username:"));
        panel.add(userField);
        panel.add(Box.createVerticalStrut(UITheme.SPACE_S));
        panel.add(createFieldLabel("Master Password:"));
        panel.add(createPasswordFieldWithToggle(passField));
        panel.add(Box.createVerticalStrut(UITheme.SPACE_XS));
        panel.add(strengthBar);
        panel.add(strengthRow);
        panel.add(Box.createVerticalStrut(UITheme.SPACE_S));
        panel.add(createFieldLabel("Confirm Master Password:"));
        panel.add(createPasswordFieldWithToggle(confirmField));
        panel.add(Box.createVerticalStrut(UITheme.SPACE_S));
        panel.add(statusLabel);
        panel.add(Box.createVerticalStrut(UITheme.SPACE_S));
        panel.add(registerButton);

        return panel;
    }

    // ==================================================================
    // Vault Dashboard View
    // ==================================================================

    private JPanel createVaultPanel() {
        JPanel vaultPanel = new JPanel(new BorderLayout());
        vaultPanel.setBackground(UITheme.getCanvasBg());

        // Header Bar
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setBackground(UITheme.getSurfaceBg());
        headerBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.getBorderColor()),
            new EmptyBorder(16, 28, 16, 28)
        ));

        JLabel logo = new JLabel("SecureVault");
        logo.setFont(UITheme.HEADING_L);
        logo.setForeground(new Color(13, 110, 253));

        userWelcomeLabel = new JLabel("Logged in as: User");
        userWelcomeLabel.setFont(UITheme.BODY);
        userWelcomeLabel.setForeground(UITheme.getTextSecondary());

        JButton themeToggle = new JButton(UITheme.isDark() ? "Light Mode" : "Dark Mode");
        themeToggle.setFont(UITheme.BODY_SMALL);
        themeToggle.setFocusPainted(false);
        themeToggle.addActionListener(e -> toggleTheme(themeToggle));

        JButton logoutBtn = new JButton("Lock Vault");
        logoutBtn.setFont(UITheme.BODY_SMALL);
        logoutBtn.setBackground(new Color(220, 53, 69));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> logout());

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        headerRight.setOpaque(false);
        headerRight.add(userWelcomeLabel);
        headerRight.add(themeToggle);
        headerRight.add(logoutBtn);

        headerBar.add(logo, BorderLayout.WEST);
        headerBar.add(headerRight, BorderLayout.EAST);

        // Action & Search Toolbar
        JPanel toolbar = new JPanel(new BorderLayout(16, 0));
        toolbar.setBorder(new EmptyBorder(18, 28, 12, 28));
        toolbar.setOpaque(false);

        searchField = new JTextField();
        searchField.setFont(UITheme.BODY);
        searchField.setPreferredSize(new Dimension(320, 38));
        searchField.putClientProperty("JTextField.placeholderText", "Search websites or accounts...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterTable(); }
            public void removeUpdate(DocumentEvent e) { filterTable(); }
            public void changedUpdate(DocumentEvent e) { filterTable(); }
        });

        JPanel searchBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        searchBox.setOpaque(false);
        JLabel searchIcon = new JLabel("Search: ");
        searchIcon.setFont(UITheme.HEADING_M);
        searchIcon.setForeground(UITheme.getTextPrimary());
        searchBox.add(searchIcon);
        searchBox.add(searchField);

        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionButtons.setOpaque(false);

        JButton addBtn = new JButton("+ Add Credential");
        addBtn.setBackground(new Color(13, 110, 253));
        addBtn.setForeground(Color.WHITE);
        addBtn.setFont(UITheme.HEADING_M);
        addBtn.setFocusPainted(false);
        addBtn.addActionListener(e -> showAddCredentialDialog());

        JButton toggleViewBtn = new JButton("Show All Passwords");
        toggleViewBtn.setFont(UITheme.BODY_SMALL);
        toggleViewBtn.addActionListener(e -> {
            passwordsRevealed = !passwordsRevealed;
            toggleViewBtn.setText(passwordsRevealed ? "Hide All Passwords" : "Show All Passwords");
            refreshTableUI();
        });

        actionButtons.add(toggleViewBtn);
        actionButtons.add(addBtn);

        toolbar.add(searchBox, BorderLayout.WEST);
        toolbar.add(actionButtons, BorderLayout.EAST);

        // Credentials Table
        String[] columns = {"ID", "Website / App", "Account / Email", "Password", "Actions"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        credentialsTable = new JTable(tableModel);
        credentialsTable.setRowHeight(40);
        credentialsTable.setShowGrid(false);
        credentialsTable.setIntercellSpacing(new Dimension(0, 0));
        credentialsTable.setFont(UITheme.BODY);
        credentialsTable.getTableHeader().setFont(UITheme.HEADING_M);
        credentialsTable.getTableHeader().setBackground(UITheme.getSurfaceBg());
        credentialsTable.getTableHeader().setForeground(UITheme.getTextPrimary());
        credentialsTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.getBorderColor()));
        credentialsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Double-click row to toggle individual password visibility
        credentialsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    toggleSelectedPasswordVisibility();
                }
            }
        });

        // Hide ID column from view
        credentialsTable.getColumnModel().getColumn(0).setMinWidth(0);
        credentialsTable.getColumnModel().getColumn(0).setMaxWidth(0);
        credentialsTable.getColumnModel().getColumn(0).setWidth(0);

        // Custom VaultTableRenderer with per-row and global show/hide
        int passwordColIndex = 3;
        for (int i = 0; i < credentialsTable.getColumnCount(); i++) {
            credentialsTable.getColumnModel().getColumn(i).setCellRenderer(
                new VaultTableRenderer(passwordColIndex, row -> {
                    int modelRow = credentialsTable.convertRowIndexToModel(row);
                    int entryId = (int) tableModel.getValueAt(modelRow, 0);
                    return passwordsRevealed || revealedEntryIds.contains(entryId);
                })
            );
        }

        JScrollPane scrollPane = new JScrollPane(credentialsTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 28, 14, 28));
        scrollPane.getViewport().setBackground(UITheme.getSurfaceBg());

        // Bottom Operations Bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(new EmptyBorder(8, 28, 16, 28));
        bottomBar.setOpaque(false);

        JLabel tipLabel = new JLabel("💡 Tip: Double-click any row (or click 'Show / Hide Selected') to reveal a specific password.");
        tipLabel.setFont(new Font(UITheme.FONT_FAMILY, Font.ITALIC, 12));
        tipLabel.setForeground(UITheme.getTextSecondary());

        JPanel buttonGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonGroup.setOpaque(false);

        JButton toggleSelectedBtn = new JButton("Show / Hide Selected");
        toggleSelectedBtn.setToolTipText("Toggle visibility for the selected application password");
        toggleSelectedBtn.addActionListener(e -> toggleSelectedPasswordVisibility());

        JButton copyBtn = new JButton("Copy Password");
        copyBtn.addActionListener(e -> copySelectedPassword());

        JButton editBtn = new JButton("Edit");
        editBtn.addActionListener(e -> showEditDialog());

        JButton deleteBtn = new JButton("Delete");
        deleteBtn.setForeground(new Color(220, 53, 69));
        deleteBtn.addActionListener(e -> deleteSelectedEntry());

        buttonGroup.add(toggleSelectedBtn);
        buttonGroup.add(copyBtn);
        buttonGroup.add(editBtn);
        buttonGroup.add(deleteBtn);

        bottomBar.add(tipLabel, BorderLayout.WEST);
        bottomBar.add(buttonGroup, BorderLayout.EAST);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(toolbar, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        centerPanel.add(bottomBar, BorderLayout.SOUTH);

        vaultPanel.add(headerBar, BorderLayout.NORTH);
        vaultPanel.add(centerPanel, BorderLayout.CENTER);

        return vaultPanel;
    }

    // ==================================================================
    // Dialogs & CRUD Operations
    // ==================================================================

    private void showAddCredentialDialog() {
        JDialog dialog = new JDialog(this, "Add New Credential", true);
        dialog.setSize(440, 380);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(22, 28, 22, 28));

        JTextField siteField = new JTextField();
        siteField.setFont(UITheme.BODY);
        JTextField userField = new JTextField();
        userField.setFont(UITheme.BODY);
        JPasswordField passField = new JPasswordField();
        passField.setFont(UITheme.BODY);

        JButton genBtn = new JButton("Generate");
        genBtn.setFont(UITheme.BODY_SMALL);
        genBtn.setFocusPainted(false);
        genBtn.addActionListener(e -> passField.setText(generateStrongPassword(16)));

        char defaultEcho = passField.getEchoChar();
        JButton showBtn = new JButton("Show");
        showBtn.setFont(UITheme.BODY_SMALL);
        showBtn.setFocusPainted(false);
        showBtn.addActionListener(e -> {
            if (passField.getEchoChar() == 0) {
                passField.setEchoChar(defaultEcho);
                showBtn.setText("Show");
            } else {
                passField.setEchoChar((char) 0);
                showBtn.setText("Hide");
            }
        });

        JPanel passActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        passActions.setOpaque(false);
        passActions.add(showBtn);
        passActions.add(genBtn);

        JPanel passRow = new JPanel(new BorderLayout(8, 0));
        passRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        passRow.add(passField, BorderLayout.CENTER);
        passRow.add(passActions, BorderLayout.EAST);

        JButton saveBtn = new JButton("Encrypt & Save");
        saveBtn.setBackground(new Color(13, 110, 253));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFont(UITheme.HEADING_M);

        saveBtn.addActionListener(e -> {
            String site = siteField.getText().trim();
            String user = userField.getText().trim();
            char[] pass = passField.getPassword();

            if (site.isEmpty() || user.isEmpty() || pass.length == 0) {
                JOptionPane.showMessageDialog(dialog, "All fields are required.", "Input Error", JOptionPane.WARNING_MESSAGE);
                Arrays.fill(pass, '\0');
                return;
            }

            try {
                VaultDao.addEntry(currentUserId, site, user, new String(pass), currentVaultKey);
                Arrays.fill(pass, '\0');
                dialog.dispose();
                loadVaultEntries();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Duplicate Entry", JOptionPane.WARNING_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        panel.add(createFieldLabel("Website / App:"));
        panel.add(siteField);
        panel.add(Box.createVerticalStrut(UITheme.SPACE_M));
        panel.add(createFieldLabel("Username / Email:"));
        panel.add(userField);
        panel.add(Box.createVerticalStrut(UITheme.SPACE_M));
        panel.add(createFieldLabel("Password:"));
        panel.add(passRow);
        panel.add(Box.createVerticalStrut(UITheme.SPACE_L));
        panel.add(saveBtn);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void showEditDialog() {
        int selectedRow = credentialsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a credential to edit.", "Selection Required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int entryId = (int) tableModel.getValueAt(selectedRow, 0);
        String currentLabel = (String) tableModel.getValueAt(selectedRow, 1);
        String currentAccUser = (String) tableModel.getValueAt(selectedRow, 2);
        String currentPlainPass = (String) tableModel.getValueAt(selectedRow, 3);

        JDialog dialog = new JDialog(this, "Edit Credential: " + currentLabel, true);
        dialog.setSize(440, 320);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(22, 28, 22, 28));

        JTextField userField = new JTextField(currentAccUser);
        userField.setFont(UITheme.BODY);
        JPasswordField passField = new JPasswordField(currentPlainPass);
        passField.setFont(UITheme.BODY);

        JButton saveBtn = new JButton("Update Credential");
        saveBtn.setBackground(new Color(25, 135, 84));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFont(UITheme.HEADING_M);

        saveBtn.addActionListener(e -> {
            String newAcc = userField.getText().trim();
            char[] newPass = passField.getPassword();

            if (newAcc.isEmpty() || newPass.length == 0) {
                JOptionPane.showMessageDialog(dialog, "Fields cannot be empty.", "Error", JOptionPane.WARNING_MESSAGE);
                Arrays.fill(newPass, '\0');
                return;
            }

            try {
                VaultDao.updateAccountUsername(entryId, currentUserId, newAcc);
                VaultDao.updatePassword(entryId, currentUserId, new String(newPass), currentVaultKey);
                Arrays.fill(newPass, '\0');
                dialog.dispose();
                loadVaultEntries();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Update failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        panel.add(createFieldLabel("Username / Email:"));
        panel.add(userField);
        panel.add(Box.createVerticalStrut(UITheme.SPACE_M));
        panel.add(createFieldLabel("Password:"));
        panel.add(createPasswordFieldWithToggle(passField));
        panel.add(Box.createVerticalStrut(UITheme.SPACE_L));
        panel.add(saveBtn);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void deleteSelectedEntry() {
        int selectedRow = credentialsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select an entry to delete.", "Selection Required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int entryId = (int) tableModel.getValueAt(selectedRow, 0);
        String label = (String) tableModel.getValueAt(selectedRow, 1);

        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to delete credentials for '" + label + "'?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                VaultDao.deleteEntry(entryId, currentUserId);
                loadVaultEntries();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to delete: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void copySelectedPassword() {
        int selectedRow = credentialsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select an entry first.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String password = (String) tableModel.getValueAt(selectedRow, 3);
        StringSelection selection = new StringSelection(password);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        lastCopiedPassword = password;

        if (clipboardClearTimer != null && clipboardClearTimer.isRunning()) {
            clipboardClearTimer.stop();
        }

        // Auto-clear clipboard after 30 seconds if it still holds the copied password
        clipboardClearTimer = new Timer(30000, e -> {
            try {
                java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                if (clipboard.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                    String currentContent = (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                    if (lastCopiedPassword != null && lastCopiedPassword.equals(currentContent)) {
                        clipboard.setContents(new StringSelection(""), null);
                        lastCopiedPassword = null;
                    }
                }
            } catch (Exception ignored) {
            }
        });
        clipboardClearTimer.setRepeats(false);
        clipboardClearTimer.start();

        JOptionPane.showMessageDialog(
            this,
            "Password copied to clipboard!\n(Clipboard will automatically clear in 30 seconds for security)",
            "Copied",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    // ==================================================================
    // Session & Data Helpers
    // ==================================================================

    private void onLoginSuccess(int userId, String username, byte[] vaultKey) {
        this.currentUserId = userId;
        this.currentUsername = username;
        this.currentVaultKey = vaultKey;

        userWelcomeLabel.setText("Logged in as: " + username);
        cardLayout.show(rootPanel, VIEW_VAULT);
        flashTransition();
        loadVaultEntries();
    }

    private void logout() {
        if (currentVaultKey != null) {
            CryptoEngine.wipeKey(currentVaultKey);
            currentVaultKey = null;
        }
        if (clipboardClearTimer != null && clipboardClearTimer.isRunning()) {
            clipboardClearTimer.stop();
        }
        try {
            java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                String currentContent = (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                if (lastCopiedPassword != null && lastCopiedPassword.equals(currentContent)) {
                    clipboard.setContents(new StringSelection(""), null);
                }
            }
        } catch (Exception ignored) {
        }
        lastCopiedPassword = null;

        currentUserId = -1;
        currentUsername = null;
        cachedEntries.clear();
        tableModel.setRowCount(0);
        passwordsRevealed = false;
        revealedEntryIds.clear();

        cardLayout.show(rootPanel, VIEW_AUTH);
        flashTransition();
    }

    private void flashTransition() {
        JPanel glass = new JPanel();
        glass.setBackground(UITheme.getCanvasBg());
        glass.setOpaque(true);
        setGlassPane(glass);
        glass.setVisible(true);

        Timer timer = new Timer(20, null);
        final float[] alpha = {1f};
        timer.addActionListener(e -> {
            alpha[0] -= 0.12f;
            if (alpha[0] <= 0f) {
                glass.setVisible(false);
                ((Timer) e.getSource()).stop();
                return;
            }
            Color base = UITheme.getCanvasBg();
            glass.setBackground(new Color(
                base.getRed(),
                base.getGreen(),
                base.getBlue(),
                Math.max(0, (int) (alpha[0] * 255))
            ));
            glass.repaint();
        });
        timer.start();
    }

    private void toggleTheme(JButton toggleBtn) {
        try {
            boolean goingDark = !UITheme.isDark();
            if (goingDark) {
                FlatDarkLaf.setup();
                if (toggleBtn != null) toggleBtn.setText(toggleBtn.getText().equals("☀") || toggleBtn.getText().equals("🌙") ? "☀" : "Light Mode");
            } else {
                FlatLightLaf.setup();
                if (toggleBtn != null) toggleBtn.setText(toggleBtn.getText().equals("☀") || toggleBtn.getText().equals("🌙") ? "🌙" : "Dark Mode");
            }
            SwingUtilities.updateComponentTreeUI(this);
            refreshTableUI();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void loadVaultEntries() {
        if (currentUserId <= 0 || currentVaultKey == null) return;

        SwingWorker<List<VaultDao.VaultEntry>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<VaultDao.VaultEntry> doInBackground() throws Exception {
                return VaultDao.getAllEntries(currentUserId, currentVaultKey);
            }

            @Override
            protected void done() {
                try {
                    cachedEntries.clear();
                    cachedEntries.addAll(get());
                    refreshTableUI();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(SecureVaultApp.this, "Failed to load vault: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void refreshTableUI() {
        String filter = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        tableModel.setRowCount(0);

        for (VaultDao.VaultEntry entry : cachedEntries) {
            if (filter.isEmpty() || entry.label().toLowerCase().contains(filter) || entry.accountUsername().toLowerCase().contains(filter)) {
                tableModel.addRow(new Object[]{
                    entry.id(),
                    entry.label(),
                    entry.accountUsername(),
                    entry.plaintextPassword(),
                    "View / Edit"
                });
            }
        }
    }

    private void filterTable() {
        refreshTableUI();
    }

    private void toggleSelectedPasswordVisibility() {
        int selectedRow = credentialsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select an entry first to show or hide its password.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = credentialsTable.convertRowIndexToModel(selectedRow);
        int entryId = (int) tableModel.getValueAt(modelRow, 0);
        if (revealedEntryIds.contains(entryId)) {
            revealedEntryIds.remove(entryId);
        } else {
            revealedEntryIds.add(entryId);
        }
        tableModel.fireTableRowsUpdated(modelRow, modelRow);
    }

    private JPanel createPasswordFieldWithToggle(JPasswordField passField) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        char defaultEcho = passField.getEchoChar();
        JButton toggleBtn = new JButton("Show");
        toggleBtn.setFont(UITheme.BODY_SMALL);
        toggleBtn.setFocusPainted(false);
        toggleBtn.setPreferredSize(new Dimension(72, 34));

        toggleBtn.addActionListener(e -> {
            if (passField.getEchoChar() == 0) {
                passField.setEchoChar(defaultEcho);
                toggleBtn.setText("Show");
            } else {
                passField.setEchoChar((char) 0);
                toggleBtn.setText("Hide");
            }
        });

        row.add(passField, BorderLayout.CENTER);
        row.add(toggleBtn, BorderLayout.EAST);
        return row;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.HEADING_M);
        label.setForeground(UITheme.getTextPrimary());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private int calculatePasswordScore(String pass) {
        if (pass == null || pass.isEmpty()) return 0;
        int score = 0;
        if (pass.length() >= 8) score++;
        if (pass.matches(".*[a-z].*")) score++;
        if (pass.matches(".*[A-Z].*")) score++;
        if (pass.matches(".*[0-9].*")) score++;
        if (pass.matches(".*[^A-Za-z0-9].*")) score++;
        return score;
    }

    private String generateStrongPassword(int length) {
        final String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final String lower = "abcdefghijklmnopqrstuvwxyz";
        final String digits = "0123456789";
        final String specials = "!@#$%^&*()-_=+[]{}";
        final String all = upper + lower + digits + specials;

        SecureRandom rand = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);

        sb.append(upper.charAt(rand.nextInt(upper.length())));
        sb.append(lower.charAt(rand.nextInt(lower.length())));
        sb.append(digits.charAt(rand.nextInt(digits.length())));
        sb.append(specials.charAt(rand.nextInt(specials.length())));

        for (int i = 4; i < length; i++) {
            sb.append(all.charAt(rand.nextInt(all.length())));
        }

        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }

    // ==================================================================
    // Main Entrypoint
    // ==================================================================

    public static void main(String[] args) {
        // Set FlatLaf Dark Mode as default
        FlatDarkLaf.setup();
        System.setProperty("flatlaf.useWindowDecorations", "true");
        System.setProperty("flatlaf.menuBarEmbedded", "true");

        SwingUtilities.invokeLater(() -> {
            SecureVaultApp app = new SecureVaultApp();
            app.setVisible(true);
        });
    }
}
