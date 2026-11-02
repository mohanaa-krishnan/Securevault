import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SecureVault Desktop Application — Java Swing GUI.
 *
 * Features:
 *   - Login & Registration with real-time password strength checklist.
 *   - Ephemeral session key handling (AES-256 vault key is wiped on logout or app exit).
 *   - Credential dashboard with live search, masked passwords, show/hide toggle, and clipboard copy.
 *   - Add, Edit, Delete credential dialogs with built-in strong password generator.
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

    public SecureVaultApp() {
        super("SecureVault — Password Manager");
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(950, 650);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

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
        authContainer.setBackground(new Color(245, 247, 250));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 225, 230), 1),
            new EmptyBorder(30, 40, 30, 40)
        ));
        card.setBackground(Color.WHITE);
        card.setPreferredSize(new Dimension(420, 560));

        JLabel titleLabel = new JLabel("SecureVault");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        titleLabel.setForeground(new Color(33, 37, 41));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Encrypted Password Manager");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitleLabel.setForeground(new Color(108, 117, 125));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.PLAIN, 14));
        tabbedPane.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Login Tab
        tabbedPane.addTab("Login", createLoginTab());

        // Register Tab
        tabbedPane.addTab("Create Account", createRegisterTab());

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(5));
        card.add(subtitleLabel);
        card.add(Box.createVerticalStrut(25));
        card.add(tabbedPane);

        authContainer.add(card);
        return authContainer;
    }

    private JPanel createLoginTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 10, 10, 10));
        panel.setBackground(Color.WHITE);

        JTextField userField = new JTextField();
        userField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JPasswordField passField = new JPasswordField();
        passField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JButton loginButton = new JButton("Unlock Vault");
        loginButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        loginButton.setBackground(new Color(13, 110, 253));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        loginButton.addActionListener(e -> {
            String username = userField.getText().trim();
            char[] password = passField.getPassword();

            if (username.isEmpty() || password.length == 0) {
                statusLabel.setText("Please enter username and password.");
                return;
            }

            statusLabel.setText("Authenticating...");
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
        panel.add(Box.createVerticalStrut(15));
        panel.add(createFieldLabel("Master Password:"));
        panel.add(passField);
        panel.add(Box.createVerticalStrut(15));
        panel.add(statusLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(loginButton);

        return panel;
    }

    private JPanel createRegisterTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(15, 10, 10, 10));
        panel.setBackground(Color.WHITE);

        JTextField userField = new JTextField();
        userField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JPasswordField passField = new JPasswordField();
        passField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JPasswordField confirmField = new JPasswordField();
        confirmField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        // Password strength meter
        JProgressBar strengthBar = new JProgressBar(0, 5);
        strengthBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        strengthBar.setValue(0);
        strengthBar.setStringPainted(false);

        JLabel strengthText = new JLabel("Password strength: None");
        strengthText.setFont(new Font("SansSerif", Font.PLAIN, 11));
        strengthText.setForeground(Color.GRAY);

        passField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateStrength(); }
            public void removeUpdate(DocumentEvent e) { updateStrength(); }
            public void changedUpdate(DocumentEvent e) { updateStrength(); }

            private void updateStrength() {
                char[] p = passField.getPassword();
                int score = calculatePasswordScore(new String(p));
                Arrays.fill(p, '\0');
                strengthBar.setValue(score);
                if (score <= 2) {
                    strengthBar.setForeground(new Color(220, 53, 69));
                    strengthText.setText("Strength: Weak");
                } else if (score <= 4) {
                    strengthBar.setForeground(new Color(255, 193, 7));
                    strengthText.setText("Strength: Moderate");
                } else {
                    strengthBar.setForeground(new Color(25, 135, 84));
                    strengthText.setText("Strength: Strong");
                }
            }
        });

        JButton registerButton = new JButton("Create Vault");
        registerButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        registerButton.setBackground(new Color(25, 135, 84));
        registerButton.setForeground(Color.WHITE);
        registerButton.setFocusPainted(false);
        registerButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        registerButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
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
            statusLabel.setText("Deriving keys and creating vault...");

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
                            "Account created successfully! You can now log in.",
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
        panel.add(Box.createVerticalStrut(10));
        panel.add(createFieldLabel("Master Password:"));
        panel.add(passField);
        panel.add(Box.createVerticalStrut(4));
        panel.add(strengthBar);
        panel.add(strengthText);
        panel.add(Box.createVerticalStrut(10));
        panel.add(createFieldLabel("Confirm Master Password:"));
        panel.add(confirmField);
        panel.add(Box.createVerticalStrut(10));
        panel.add(statusLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(registerButton);

        return panel;
    }

    // ==================================================================
    // Vault Dashboard View
    // ==================================================================

    private JPanel createVaultPanel() {
        JPanel vaultPanel = new JPanel(new BorderLayout());
        vaultPanel.setBackground(new Color(248, 249, 250));

        // Header Bar
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setBackground(Color.WHITE);
        headerBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(222, 226, 230)),
            new EmptyBorder(15, 25, 15, 25)
        ));

        JLabel logo = new JLabel("SecureVault");
        logo.setFont(new Font("SansSerif", Font.BOLD, 20));
        logo.setForeground(new Color(13, 110, 253));

        userWelcomeLabel = new JLabel("Logged in as: User");
        userWelcomeLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        userWelcomeLabel.setForeground(new Color(108, 117, 125));

        JButton logoutBtn = new JButton("Lock Vault");
        logoutBtn.setFont(new Font("SansSerif", Font.PLAIN, 13));
        logoutBtn.setBackground(new Color(220, 53, 69));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> logout());

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        headerRight.setOpaque(false);
        headerRight.add(userWelcomeLabel);
        headerRight.add(logoutBtn);

        headerBar.add(logo, BorderLayout.WEST);
        headerBar.add(headerRight, BorderLayout.EAST);

        // Action & Search Toolbar
        JPanel toolbar = new JPanel(new BorderLayout(15, 0));
        toolbar.setBorder(new EmptyBorder(15, 25, 10, 25));
        toolbar.setOpaque(false);

        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(300, 36));
        searchField.putClientProperty("JTextField.placeholderText", "Search websites or apps...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterTable(); }
            public void removeUpdate(DocumentEvent e) { filterTable(); }
            public void changedUpdate(DocumentEvent e) { filterTable(); }
        });

        JPanel searchBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        searchBox.setOpaque(false);
        JLabel searchIcon = new JLabel("Search: ");
        searchIcon.setFont(new Font("SansSerif", Font.BOLD, 13));
        searchBox.add(searchIcon);
        searchBox.add(searchField);

        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionButtons.setOpaque(false);

        JButton addBtn = new JButton("+ Add Credential");
        addBtn.setBackground(new Color(13, 110, 253));
        addBtn.setForeground(Color.WHITE);
        addBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        addBtn.setFocusPainted(false);
        addBtn.addActionListener(e -> showAddCredentialDialog());

        JButton toggleViewBtn = new JButton("Show All Passwords");
        toggleViewBtn.setFont(new Font("SansSerif", Font.PLAIN, 13));
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
        credentialsTable.setRowHeight(38);
        credentialsTable.setFont(new Font("SansSerif", Font.PLAIN, 14));
        credentialsTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        credentialsTable.getTableHeader().setBackground(new Color(241, 243, 245));
        credentialsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Hide ID column from view
        credentialsTable.getColumnModel().getColumn(0).setMinWidth(0);
        credentialsTable.getColumnModel().getColumn(0).setMaxWidth(0);
        credentialsTable.getColumnModel().getColumn(0).setWidth(0);

        // Mask password column renderer
        credentialsTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int col) {
                String text = (value != null) ? value.toString() : "";
                if (!passwordsRevealed && !text.isEmpty()) {
                    text = "••••••••••••";
                }
                return super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, col);
            }
        });

        JScrollPane scrollPane = new JScrollPane(credentialsTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 25, 15, 25));
        scrollPane.getViewport().setBackground(Color.WHITE);

        // Bottom Operations Bar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        bottomBar.setBorder(new EmptyBorder(0, 25, 10, 25));
        bottomBar.setOpaque(false);

        JButton copyBtn = new JButton("Copy Password");
        copyBtn.addActionListener(e -> copySelectedPassword());

        JButton editBtn = new JButton("Edit");
        editBtn.addActionListener(e -> showEditDialog());

        JButton deleteBtn = new JButton("Delete");
        deleteBtn.setForeground(new Color(220, 53, 69));
        deleteBtn.addActionListener(e -> deleteSelectedEntry());

        bottomBar.add(copyBtn);
        bottomBar.add(editBtn);
        bottomBar.add(deleteBtn);

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
        dialog.setSize(420, 360);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 25, 20, 25));

        JTextField siteField = new JTextField();
        JTextField userField = new JTextField();
        JPasswordField passField = new JPasswordField();

        JButton genBtn = new JButton("Generate Password");
        genBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        genBtn.addActionListener(e -> passField.setText(generateStrongPassword(16)));

        JPanel passRow = new JPanel(new BorderLayout(8, 0));
        passRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        passRow.add(passField, BorderLayout.CENTER);
        passRow.add(genBtn, BorderLayout.EAST);

        JButton saveBtn = new JButton("Encrypt & Save");
        saveBtn.setBackground(new Color(13, 110, 253));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFont(new Font("SansSerif", Font.BOLD, 13));

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
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        panel.add(createFieldLabel("Website / App:"));
        panel.add(siteField);
        panel.add(Box.createVerticalStrut(12));
        panel.add(createFieldLabel("Username / Email:"));
        panel.add(userField);
        panel.add(Box.createVerticalStrut(12));
        panel.add(createFieldLabel("Password:"));
        panel.add(passRow);
        panel.add(Box.createVerticalStrut(25));
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
        dialog.setSize(420, 300);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 25, 20, 25));

        JTextField userField = new JTextField(currentAccUser);
        JPasswordField passField = new JPasswordField(currentPlainPass);

        JButton saveBtn = new JButton("Update Credential");
        saveBtn.setBackground(new Color(25, 135, 84));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFont(new Font("SansSerif", Font.BOLD, 13));

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
        panel.add(Box.createVerticalStrut(15));
        panel.add(createFieldLabel("Password:"));
        panel.add(passField);
        panel.add(Box.createVerticalStrut(25));
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
        JOptionPane.showMessageDialog(this, "Password copied to clipboard!", "Copied", JOptionPane.INFORMATION_MESSAGE);
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
        loadVaultEntries();
    }

    private void logout() {
        if (currentVaultKey != null) {
            CryptoEngine.wipeKey(currentVaultKey);
            currentVaultKey = null;
        }
        currentUserId = -1;
        currentUsername = null;
        cachedEntries.clear();
        tableModel.setRowCount(0);
        passwordsRevealed = false;

        cardLayout.show(rootPanel, VIEW_AUTH);
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

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 13));
        label.setForeground(new Color(73, 80, 87));
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
        // Set Look and Feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            SecureVaultApp app = new SecureVaultApp();
            app.setVisible(true);
        });
    }
}
