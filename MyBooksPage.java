import java.awt.BorderLayout;
import java.awt.Font;
import java.sql.*;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public class MyBooksPage extends JFrame {

    private JTable bookTable;
    private DefaultTableModel tableModel;
    private JButton returnButton;
    private JButton backButton;

    private String userId;

    public MyBooksPage(String userId) {
        super("My Books");
        this.userId = userId;

        setSize(800, 600);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        loadBorrowedBooks();
        setVisible(true);
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel headerLabel = new JLabel("My Borrowed Books", JLabel.CENTER);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        mainPanel.add(headerLabel, BorderLayout.NORTH);

        // loanId is included in the model but will be hidden later
        String[] columnNames = {"Loan ID", "Book ID", "Title", "Author", "Borrowed Date", "Due Date"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        bookTable = new JTable(tableModel);
        bookTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(bookTable);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        returnButton = new JButton("Return Selected Book");
        backButton = new JButton("Back");

        buttonPanel.add(returnButton);
        buttonPanel.add(backButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);

        returnButton.addActionListener(e -> handleReturn());
        backButton.addActionListener(e -> {
            this.dispose();
            new UserPage().setVisible(true);
        });
    }

    private void loadBorrowedBooks() {
        tableModel.setRowCount(0);

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT l.loanId, l.bookId, b.bookTitle, b.bookAuthor, l.borrowedDate, l.dueDate " +
                         "FROM loans l " +
                         "LEFT JOIN books b ON l.bookId = b.bookId " +
                         "WHERE l.userId = ? AND l.returned = 0";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, userId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int loanId = rs.getInt("loanId");
                        String bookId = rs.getString("bookId");
                        String title = rs.getString("bookTitle");
                        String author = rs.getString("bookAuthor");
                        String borrowedDate = rs.getString("borrowedDate");
                        String dueDate = rs.getString("dueDate");

                        tableModel.addRow(new Object[]{loanId, bookId, title, author, borrowedDate, dueDate});
                    }
                }
            }

            // Hide loanId column from the table view
            bookTable.removeColumn(bookTable.getColumnModel().getColumn(0));

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void handleReturn() {
        int selectedRow = bookTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a book to return.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // loanId is hidden in the table view, but still in the model
        int loanId = (int) tableModel.getValueAt(selectedRow, 0);
        String bookTitle = (String) tableModel.getValueAt(selectedRow, 2);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to return \"" + bookTitle + "\"?", "Confirm Return",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                conn.setAutoCommit(false);

                // 1. Mark loan as returned
                String updateLoan = "UPDATE loans SET returned = 1 WHERE loanId = ? AND userId = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(updateLoan)) {
                    pstmt.setInt(1, loanId);
                    pstmt.setString(2, userId);
                    pstmt.executeUpdate();
                }

                // 2. Increase book quantity by 1
                String updateBook = "UPDATE books SET bookQuantity = bookQuantity + 1 " +
                                    "WHERE bookId = (SELECT bookId FROM loans WHERE loanId = ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(updateBook)) {
                    pstmt.setInt(1, loanId);
                    pstmt.executeUpdate();
                }

                conn.commit();

                JOptionPane.showMessageDialog(this,
                        "Book \"" + bookTitle + "\" has been returned successfully.",
                        "Return Successful",
                        JOptionPane.INFORMATION_MESSAGE);

                loadBorrowedBooks(); // Refresh table
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error returning book: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MyBooksPage("1")); // Example userId
    }
}