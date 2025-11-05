import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class JeopardyGUI {
    private JFrame frame;
    private JPanel mainPanel;
    private JPanel boardPanel;
    private JPanel cluePanel;
    private JPanel scorePanel;
    private JPanel controlsPanel;
    private JPanel bottomPanel;

    private LinkedHashMap<String, List<Clue>> byCategory = new LinkedHashMap<>();
    private List<String> categories = new ArrayList<>();
    private List<Clue> all = new ArrayList<>();
    private Contestant[] contestants;
    private JLabel[] scoreLabels;

    private String currentCategory;
    private int currentValue;
    private Clue currentClue;

    private boolean doubleJeopardy = false;
    private Clue finalJeopardy;


    private static class Clue {
        String category;
        int value;
        String question;
        String answer;
        boolean used;
    }

    private static class Contestant {
        String name;
        int score;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new JeopardyGUI().start());
    }

    private void start() {
        frame = new JFrame("Jeopardy!");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 750);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(10, 10));
        frame.getContentPane().setBackground(new Color(0, 0, 128));

        createScorePanel();
        createControlsPanel();
        createBottomPanel(); // combines both

        createBoardPlaceholder();
        loadCluesDialog();
        getContestantNames();
        rebuildBoard();

        frame.setVisible(true);
    }

    /** Combines scores + controls into a single bottom bar */
    private void createBottomPanel() {
        bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.setBackground(new Color(0, 0, 128));

        bottomPanel.add(scorePanel, BorderLayout.CENTER);
        bottomPanel.add(controlsPanel, BorderLayout.EAST);

        frame.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void createScorePanel() {
        scorePanel = new JPanel();
        scorePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 30, 10));
        scorePanel.setBackground(new Color(0, 0, 128));

        contestants = new Contestant[3];
        scoreLabels = new JLabel[3];
        for (int i = 0; i < 3; i++) {
            contestants[i] = new Contestant();
            contestants[i].name = "Contestant " + (i + 1);
            contestants[i].score = 0;

            scoreLabels[i] = new JLabel(contestants[i].name + ": $0");
            scoreLabels[i].setFont(new Font("SansSerif", Font.BOLD, 22));
            scoreLabels[i].setForeground(new Color(255, 215, 0));
            scorePanel.add(scoreLabels[i]);
        }
    }

    private void createControlsPanel() {
        controlsPanel = new JPanel();
        controlsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        controlsPanel.setBackground(new Color(0, 0, 128));

        JButton fjBtn = new JButton("Final Jeopardy");
        fjBtn.setFont(new Font("SansSerif", Font.BOLD, 18));
        fjBtn.addActionListener(e -> showFinalJeopardyCategorySlide());
        controlsPanel.add(fjBtn);
    }

    private void createBoardPlaceholder() {
        boardPanel = new JPanel(new BorderLayout());
        boardPanel.setBackground(new Color(0, 0, 128));
        JLabel label = new JLabel("<html><center><font color='white'>Load a CSV file to start the game.</font></center></html>", SwingConstants.CENTER);
        label.setFont(new Font("SansSerif", Font.BOLD, 18));
        boardPanel.add(label, BorderLayout.CENTER);
        frame.add(boardPanel, BorderLayout.CENTER);
    }

    private void loadCluesDialog() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                loadCluesFromCSV(chooser.getSelectedFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Failed to load: " + ex.getMessage());
            }
        }
    }

    private void loadCluesFromCSV(File file) throws IOException {
        byCategory.clear();
        categories.clear();
        all.clear();
        finalJeopardy = null;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            boolean firstLine = true;
            boolean inFinalJeopardy = false;
            Pattern csvPattern = Pattern.compile("\"([^\"]*)\"|([^,]+)");

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Handle Final Jeopardy section start
                if (line.startsWith("#")) {
                    if (line.toLowerCase().contains("finaljeopardy")) {
                        inFinalJeopardy = true;
                    }
                    continue;
                }

                List<String> partsList = new ArrayList<>();
                Matcher m = csvPattern.matcher(line);
                while (m.find()) {
                    if (m.group(1) != null) partsList.add(m.group(1).trim());
                    else if (m.group(2) != null) partsList.add(m.group(2).trim());
                }

                // Remove trailing empty fields
                while (!partsList.isEmpty() && partsList.get(partsList.size() - 1).isEmpty())
                    partsList.remove(partsList.size() - 1);

                String[] parts = partsList.toArray(new String[0]);
                if (parts.length < 3) continue;

                if (inFinalJeopardy) {
                    // Expect: Category, Question, Answer
                    if (parts.length >= 3)
                        finalJeopardy = new Clue(parts[0], 0, parts[1], parts[2]);
                    continue;
                }

                // Skip header or malformed line
                if (firstLine && !isNumeric(parts[1].trim())) {
                    firstLine = false;
                    continue;
                }
                firstLine = false;
                if (!isNumeric(parts[1].trim())) continue;

                Clue c = new Clue();
                c.category = parts[0].trim();
                c.value = Integer.parseInt(parts[1].trim());
                c.question = parts.length > 2 ? parts[2].trim() : "";
                c.answer = parts.length > 3 ? parts[3].trim() : "";

                byCategory.computeIfAbsent(c.category, k -> {
                    categories.add(k);
                    return new ArrayList<>();
                }).add(c);
                all.add(c);
            }
        }

        for (var cat : categories)
            byCategory.get(cat).sort(Comparator.comparingInt(x -> x.value));
    }


    /** Utility to check if string is numeric */
    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    private void rebuildBoard() {
        frame.remove(boardPanel);
        int cols = categories.size();
        int rows = byCategory.values().stream().mapToInt(List::size).max().orElse(5);

        JPanel grid = new JPanel(new GridLayout(rows + 1, cols, 2, 2));
        grid.setBackground(Color.BLACK);

        // Category headers
        for (String cat : categories) {
            JLabel lbl = new JLabel("<html><center>" + cat.toUpperCase() + "</center></html>", SwingConstants.CENTER);
            lbl.setFont(new Font("Bookman Old Style", Font.BOLD, 22));
            lbl.setForeground(Color.WHITE);
            lbl.setOpaque(true);
            lbl.setBackground(new Color(0, 0, 128));
            lbl.setBorder(BorderFactory.createLineBorder(Color.BLACK, 4));
            grid.add(lbl);
        }

        // Clue buttons
        for (int r = 0; r < rows; r++) {
            for (String cat : categories) {
                List<Clue> list = byCategory.get(cat);
                if (r < list.size()) {
                    Clue clue = list.get(r);
                    JButton btn = new JButton("$" + clue.value);
                    btn.setFont(new Font("Bookman Old Style", Font.BOLD, 24));
                    btn.setBackground(new Color(0, 0, 128));
                    btn.setForeground(new Color(255, 215, 0));
                    btn.addActionListener(e -> showClue(clue, btn));
                    grid.add(btn);
                } else {
                    JPanel empty = new JPanel();
                    empty.setBackground(new Color(0, 0, 128));
                    grid.add(empty);
                }
            }
        }

        boardPanel = new JPanel(new BorderLayout());
        boardPanel.add(grid, BorderLayout.CENTER);
        frame.add(boardPanel, BorderLayout.CENTER);

        frame.revalidate();
        frame.repaint();
    }

    private void showClue(Clue clue, JButton sourceBtn) {
        if (clue.used) return;
        clue.used = true;
        sourceBtn.setEnabled(false);

        JPanel cluePanel = new JPanel(new BorderLayout());
        cluePanel.setBackground(new Color(0, 0, 128));

        JLabel qLabel = new JLabel("<html><center>" + clue.question.toUpperCase() + "</center></html>", SwingConstants.CENTER);
        qLabel.setFont(new Font("Bookman Old Style", Font.PLAIN, 40));
        qLabel.setForeground(Color.WHITE);
        cluePanel.add(qLabel, BorderLayout.CENTER);

        JPanel bottomBtns = new JPanel();
        bottomBtns.setBackground(new Color(0, 0, 128));

        JButton buzzBtn = new JButton("Buzz In");
        buzzBtn.setFont(new Font("SansSerif", Font.BOLD, 20));
        buzzBtn.addActionListener(e -> showBuzzOptions(clue));

        JButton backBtn = new JButton("No Buzz");
        backBtn.setFont(new Font("SansSerif", Font.BOLD, 20));
        backBtn.addActionListener(e -> backToBoard());

        bottomBtns.add(buzzBtn);
        bottomBtns.add(backBtn);
        cluePanel.add(bottomBtns, BorderLayout.SOUTH);

        frame.setContentPane(cluePanel);
        frame.revalidate();
        frame.repaint();
    }

    private void showBuzzOptions(Clue clue) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        p.setBackground(new Color(0, 0, 128));

        for (Contestant c : contestants) {
            JButton b = new JButton(c.name);
            b.setFont(new Font("SansSerif", Font.BOLD, 22));
            b.addActionListener(e -> handleBuzz(c, clue));
            p.add(b);
        }

        frame.setContentPane(p);
        frame.revalidate();
        frame.repaint();
    }

    private void handleBuzz(Contestant c, Clue clue) {
        int correct = JOptionPane.showConfirmDialog(frame, "Did " + c.name + " answer correctly?", "Result", JOptionPane.YES_NO_OPTION);
        if (correct == JOptionPane.YES_OPTION) c.score += clue.value;
        else c.score -= clue.value;
        updateScoreLabels();
        backToBoard();
    }

    private void updateScoreLabels() {
        for (int i = 0; i < contestants.length; i++)
            scoreLabels[i].setText(contestants[i].name + ": $" + contestants[i].score);
    }

    private void getContestantNames() {
        for (int i = 0; i < contestants.length; i++) {
            String name = JOptionPane.showInputDialog(frame, "Enter name for contestant " + (i + 1) + ":", contestants[i].name);
            if (name != null && !name.isBlank()) contestants[i].name = name.trim();
        }
        updateScoreLabels();
    }

    private void showFinalJeopardyCategorySlide() {
        JOptionPane.showMessageDialog(frame, "Final Jeopardy not implemented yet!");
    }

    private void backToBoard() {
        frame.setContentPane(new JPanel(new BorderLayout()) {{
            add(boardPanel, BorderLayout.CENTER);
            add(bottomPanel, BorderLayout.SOUTH);
        }});
        frame.revalidate();
        frame.repaint();
    }
}
